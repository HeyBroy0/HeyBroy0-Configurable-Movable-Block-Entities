package io.github.heybroy0.cmbe.adhesion;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.heybroy0.cmbe.CmbeMod;
import io.github.heybroy0.cmbe.config.BlockConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Precomputed state-specific face contact geometry. Runtime piston checks only read this cache.
 *
 * For every analyzed state, face, and integer depth (1..16), the cache stores the
 * largest fully occupied square in the projected 16x16 face grid. Changing either
 * threshold therefore requires no new shape calculations.
 */
public final class AdhesionAnalysisCache {
    private static final int SCHEMA_VERSION = 1;
    private static final int MAX_STATES_PER_BLOCK = 4096;
    private static final int DEPTH_COUNT = 16;
    private static final int METRIC_COUNT = Direction.values().length * DEPTH_COUNT;
    private static final double EPSILON = 1.0E-9;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
        .resolve("cmbe/adhesion-analysis-cache.json");

    private static final Map<String, Map<String, int[]>> METRICS = new ConcurrentHashMap<>();
    private static final Map<String, String> FINGERPRINTS = new ConcurrentHashMap<>();
    private static final Map<String, List<String>> GEOMETRY_PROPERTIES = new ConcurrentHashMap<>();
    private static final Map<String, List<String>> DIRECTION_PROPERTIES = new ConcurrentHashMap<>();
    private static final Set<String> TRUNCATED_BLOCKS = ConcurrentHashMap.newKeySet();

    private static boolean loaded;

    private AdhesionAnalysisCache() {}

    /**
     * Runs after block registration. It must be called on the main thread because
     * some modded shape implementations consult world or registry state.
     */
    private static List<Map.Entry<Identifier, Block>> analysisQueue = List.of();
    private static int analysisIndex;
    private static boolean analysisPrepared;

    public static synchronized boolean isAnalysisComplete() {
        load();
        prepareAnalysisQueue();
        return analysisIndex >= analysisQueue.size();
    }

    /**
     * Analyze a bounded number of blocks on the calling thread. The client calls
     * this during title-screen ticks, and the server calls it during server ticks.
     */
    public static synchronized void analyzeRegisteredBlocks(int maxBlocks) {
        if (maxBlocks < 1) return;
        load();
        prepareAnalysisQueue();

        int analyzed = 0;
        while (analysisIndex < analysisQueue.size() && analyzed < maxBlocks) {
            analyzeBlock(analysisQueue.get(analysisIndex++));
            analyzed++;
        }

        // Persist batches instead of repeatedly rewriting the full cache on every tick.
        if (analyzed > 0 && (analysisIndex % 8 == 0 || analysisIndex >= analysisQueue.size())) {
            save();
        }
    }

    /** Synchronous fallback for tools or environments without a tick loop. */
    public static synchronized void analyzeRegisteredBlocks() {
        while (!isAnalysisComplete()) {
            analyzeRegisteredBlocks(32);
        }
    }

    private static void prepareAnalysisQueue() {
        if (analysisPrepared) return;
        List<Map.Entry<Identifier, Block>> entries = new ArrayList<>();
        for (Map.Entry<ResourceKey<Block>, Block> entry : BuiltInRegistries.BLOCK.entrySet()) {
            entries.add(Map.entry(entry.getKey().identifier(), entry.getValue()));
        }
        analysisQueue = List.copyOf(entries);
        analysisIndex = 0;
        analysisPrepared = true;
    }

    private static void analyzeBlock(Map.Entry<Identifier, Block> entry) {
        String blockId = entry.getKey().toString();
        Block block = entry.getValue();
        String fingerprint = fingerprint(block);

        if (fingerprint.equals(FINGERPRINTS.get(blockId))
            && METRICS.containsKey(blockId)
            && !METRICS.get(blockId).isEmpty()) {
            return;
        }

        List<BlockState> states = block.getStateDefinition().getPossibleStates();
        int stateCount = Math.min(states.size(), MAX_STATES_PER_BLOCK);
        Map<String, int[]> blockMetrics = new LinkedHashMap<>();
        List<String> shapeSignatures = new ArrayList<>(stateCount);

        for (int i = 0; i < stateCount; i++) {
            BlockState state = states.get(i);
            String stateKey = state.toString();

            try {
                VoxelShape outline = state.getShape(
                    EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty()
                );
                VoxelShape collision = state.getCollisionShape(
                    EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty()
                );

                blockMetrics.put(stateKey + "|OUTLINE", measure(outline));
                blockMetrics.put(stateKey + "|COLLISION", measure(collision));
                shapeSignatures.add(signature(outline));
            } catch (RuntimeException exception) {
                // Some modded blocks require a real world context. Fail closed, and
                // let their mod provide an adapter rather than calling them in a piston.
                blockMetrics.put(stateKey + "|OUTLINE", new int[METRIC_COUNT]);
                blockMetrics.put(stateKey + "|COLLISION", new int[METRIC_COUNT]);
                shapeSignatures.add("context-dependent");
                CmbeMod.LOGGER.debug(
                    "Could not analyze geometry for {} state {}.", blockId, stateKey, exception
                );
            }
        }

        List<Property<?>> properties = new ArrayList<>(block.getStateDefinition().getProperties());
        GEOMETRY_PROPERTIES.put(
            blockId, findGeometryProperties(states.subList(0, stateCount), properties, shapeSignatures)
        );
        DIRECTION_PROPERTIES.put(blockId, findDirectionProperties(properties));
        METRICS.put(blockId, blockMetrics);
        FINGERPRINTS.put(blockId, fingerprint);

        if (states.size() > MAX_STATES_PER_BLOCK) TRUNCATED_BLOCKS.add(blockId);
        else TRUNCATED_BLOCKS.remove(blockId);
    }

    public static int contactSquare(BlockState state, Direction face, BlockConfig config) {
        load();
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        Map<String, int[]> blockMetrics = METRICS.get(blockId);
        if (blockMetrics == null) return 0;

        String source = config.geometrySource == BlockConfig.GeometrySource.COLLISION
            ? "COLLISION" : "OUTLINE";
        int[] values = blockMetrics.get(state + "|" + source);
        if (values == null || values.length != METRIC_COUNT) return 0;

        int depth = Math.max(1, Math.min(16, config.hitboxDepth));
        return values[face.ordinal() * DEPTH_COUNT + depth - 1];
    }

    public static boolean hasAnyHitboxFace(BlockState state, BlockConfig config) {
        for (Direction face : Direction.values()) {
            if (contactSquare(state, face, config) >= config.hitboxMinimumSquare) return true;
        }
        return false;
    }

    public static List<String> geometryProperties(String blockId) {
        load();
        return GEOMETRY_PROPERTIES.getOrDefault(blockId, List.of());
    }

    public static List<String> directionProperties(String blockId) {
        load();
        return DIRECTION_PROPERTIES.getOrDefault(blockId, List.of());
    }

    public static boolean isTruncated(String blockId) {
        load();
        return TRUNCATED_BLOCKS.contains(blockId);
    }

    private static int[] measure(VoxelShape shape) {
        int[] result = new int[METRIC_COUNT];
        List<AABB> boxes = shape.toAabbs();
        if (boxes.isEmpty()) return result;

        for (Direction face : Direction.values()) {
            for (int depth = 1; depth <= DEPTH_COUNT; depth++) {
                boolean[][] occupied = new boolean[16][16];

                for (int u = 0; u < 16; u++) {
                    for (int v = 0; v < 16; v++) {
                        for (AABB box : boxes) {
                            if (intersectsFaceColumn(box, face, depth, u, v)) {
                                occupied[u][v] = true;
                                break;
                            }
                        }
                    }
                }

                result[face.ordinal() * DEPTH_COUNT + depth - 1] = largestFilledSquare(occupied);
            }
        }
        return result;
    }

    private static boolean intersectsFaceColumn(AABB box, Direction face, int depth, int u, int v) {
        double a0 = u / 16.0;
        double a1 = (u + 1) / 16.0;
        double b0 = v / 16.0;
        double b1 = (v + 1) / 16.0;
        double d = depth / 16.0;

        return switch (face) {
            case WEST -> overlaps(box.minX, box.maxX, 0.0, d)
                && overlaps(box.minZ, box.maxZ, a0, a1)
                && overlaps(box.minY, box.maxY, b0, b1);
            case EAST -> overlaps(box.minX, box.maxX, 1.0 - d, 1.0)
                && overlaps(box.minZ, box.maxZ, a0, a1)
                && overlaps(box.minY, box.maxY, b0, b1);
            case DOWN -> overlaps(box.minY, box.maxY, 0.0, d)
                && overlaps(box.minX, box.maxX, a0, a1)
                && overlaps(box.minZ, box.maxZ, b0, b1);
            case UP -> overlaps(box.minY, box.maxY, 1.0 - d, 1.0)
                && overlaps(box.minX, box.maxX, a0, a1)
                && overlaps(box.minZ, box.maxZ, b0, b1);
            case NORTH -> overlaps(box.minZ, box.maxZ, 0.0, d)
                && overlaps(box.minX, box.maxX, a0, a1)
                && overlaps(box.minY, box.maxY, b0, b1);
            case SOUTH -> overlaps(box.minZ, box.maxZ, 1.0 - d, 1.0)
                && overlaps(box.minX, box.maxX, a0, a1)
                && overlaps(box.minY, box.maxY, b0, b1);
        };
    }

    private static boolean overlaps(double minA, double maxA, double minB, double maxB) {
        return Math.min(maxA, maxB) - Math.max(minA, minB) > EPSILON;
    }

    private static int largestFilledSquare(boolean[][] cells) {
        int[][] dp = new int[16][16];
        int maximum = 0;

        for (int u = 0; u < 16; u++) {
            for (int v = 0; v < 16; v++) {
                if (!cells[u][v]) continue;
                int left = v == 0 ? 0 : dp[u][v - 1];
                int up = u == 0 ? 0 : dp[u - 1][v];
                int diagonal = u == 0 || v == 0 ? 0 : dp[u - 1][v - 1];
                dp[u][v] = 1 + Math.min(diagonal, Math.min(left, up));
                maximum = Math.max(maximum, dp[u][v]);
            }
        }
        return maximum;
    }

    private static String signature(VoxelShape shape) {
        List<String> boxes = new ArrayList<>();
        for (AABB box : shape.toAabbs()) {
            boxes.add(
                box.minX + "," + box.minY + "," + box.minZ + ","
                    + box.maxX + "," + box.maxY + "," + box.maxZ
            );
        }
        boxes.sort(Comparator.naturalOrder());
        return String.join(";", boxes);
    }

    private static List<String> findGeometryProperties(
        List<BlockState> states,
        List<Property<?>> properties,
        List<String> signatures
    ) {
        Set<String> result = new TreeSet<>();

        for (Property<?> property : properties) {
            Map<String, Set<String>> signaturesByOtherValues = new HashMap<>();
            for (int i = 0; i < states.size(); i++) {
                String key = tupleWithout(states.get(i), properties, property);
                signaturesByOtherValues.computeIfAbsent(key, unused -> new TreeSet<>()).add(signatures.get(i));
            }
            if (signaturesByOtherValues.values().stream().anyMatch(values -> values.size() > 1)) {
                result.add(property.getName());
            }
        }
        return List.copyOf(result);
    }

    private static String tupleWithout(BlockState state, List<Property<?>> properties, Property<?> excluded) {
        StringBuilder key = new StringBuilder();
        for (Property<?> property : properties) {
            if (property == excluded) continue;
            key.append(property.getName()).append('=').append(propertyValueName(state, property)).append(';');
        }
        return key.toString();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String propertyValueName(BlockState state, Property property) {
        Comparable value = (Comparable) state.getValue(property);
        return property.getName(value);
    }

    private static List<String> findDirectionProperties(List<Property<?>> properties) {
        List<String> result = new ArrayList<>();
        for (Property<?> property : properties) {
            Collection<?> values = property.getPossibleValues();
            if (!values.isEmpty() && values.stream().allMatch(Direction.class::isInstance)) {
                result.add(property.getName());
            }
        }
        return List.copyOf(result);
    }

    private static String fingerprint(Block block) {
        StringBuilder value = new StringBuilder(block.getClass().getName());
        for (Property<?> property : block.getStateDefinition().getProperties()) {
            value.append('|').append(property.getName()).append(':').append(property.getClass().getName());
            List<String> candidates = new ArrayList<>();
            for (Object candidate : property.getPossibleValues()) candidates.add(String.valueOf(candidate));
            candidates.sort(Comparator.naturalOrder());
            candidates.forEach(candidate -> value.append(',').append(candidate));
        }
        return Integer.toHexString(value.toString().hashCode()) + ":" + block.getStateDefinition().getPossibleStates().size();
    }

    private static synchronized void load() {
        if (loaded) return;
        loaded = true;
        if (!Files.exists(FILE)) return;

        try (BufferedReader reader = Files.newBufferedReader(FILE)) {
            CacheFile file = GSON.fromJson(reader, CacheFile.class);
            if (file == null || file.schemaVersion != SCHEMA_VERSION || !"26.3".equals(file.minecraftVersion)) {
                return;
            }
            if (file.metrics != null) METRICS.putAll(file.metrics);
            if (file.fingerprints != null) FINGERPRINTS.putAll(file.fingerprints);
            if (file.geometryProperties != null) GEOMETRY_PROPERTIES.putAll(file.geometryProperties);
            if (file.directionProperties != null) DIRECTION_PROPERTIES.putAll(file.directionProperties);
            if (file.truncatedBlocks != null) TRUNCATED_BLOCKS.addAll(file.truncatedBlocks);
        } catch (Exception exception) {
            CmbeMod.LOGGER.warn("Could not read adhesion analysis cache; it will be regenerated.", exception);
        }
    }

    private static synchronized void save() {
        try {
            Files.createDirectories(FILE.getParent());
            CacheFile file = new CacheFile();
            file.schemaVersion = SCHEMA_VERSION;
            file.minecraftVersion = "26.3";
            file.metrics = new HashMap<>(METRICS);
            file.fingerprints = new HashMap<>(FINGERPRINTS);
            file.geometryProperties = new HashMap<>(GEOMETRY_PROPERTIES);
            file.directionProperties = new HashMap<>(DIRECTION_PROPERTIES);
            file.truncatedBlocks = new ArrayList<>(TRUNCATED_BLOCKS);

            Path temporary = FILE.resolveSibling(FILE.getFileName() + ".tmp");
            try (BufferedWriter writer = Files.newBufferedWriter(temporary)) {
                GSON.toJson(file, writer);
            }
            try {
                Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException unsupportedAtomicMove) {
                Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            CmbeMod.LOGGER.error("Unable to save adhesion analysis cache.", exception);
        }
    }

    private static final class CacheFile {
        int schemaVersion;
        String minecraftVersion;
        Map<String, Map<String, int[]>> metrics = new HashMap<>();
        Map<String, String> fingerprints = new HashMap<>();
        Map<String, List<String>> geometryProperties = new HashMap<>();
        Map<String, List<String>> directionProperties = new HashMap<>();
        List<String> truncatedBlocks = new ArrayList<>();
    }
}
