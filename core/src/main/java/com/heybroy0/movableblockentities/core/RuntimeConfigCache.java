package com.heybroy0.movableblockentities.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/**
 * Runtime-facing snapshot of the settings edited by the optional GUI.
 *
 * <p>This class intentionally has no Minecraft, Fabric, or GUI dependencies. Runtime
 * implementations load the generated cache file and query settings by block identifier.
 * The GUI owns generation of the file; consumers of this API do not need to know about
 * folders, selectors, screens, or the editor's persistence format.</p>
 */
public final class RuntimeConfigCache {
    public static final int FORMAT_VERSION = 1;

    private static volatile Map<String, BlockSettings> settings = Map.of();

    private RuntimeConfigCache() {}

    /**
     * Reads a previously generated cache. A missing file means that no custom settings
     * have been exported yet and clears the in-memory snapshot.
     *
     * @throws IOException when the file exists but cannot be read or has a newer format
     */
    public static synchronized void load(Path file) throws IOException {
        if (file == null) throw new IllegalArgumentException("Cache file path cannot be null");
        if (!Files.exists(file)) {
            settings = Map.of();
            return;
        }

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        }

        int version = readInt(properties, "format.version", 0);
        if (version > FORMAT_VERSION) {
            throw new IOException("Unsupported runtime config cache format: " + version);
        }

        Map<String, BlockSettings> loaded = new TreeMap<>();
        for (String property : properties.stringPropertyNames()) {
            if (!property.startsWith("block.") || !property.endsWith(".id")) continue;

            String encoded = property.substring("block.".length(), property.length() - ".id".length());
            String blockId = properties.getProperty(property, "").trim();
            if (blockId.isEmpty()) continue;

            String prefix = "block." + encoded + ".";
            Map<String, Boolean> faces = new TreeMap<>();
            for (String face : new String[]{"FRONT", "BACK", "LEFT", "RIGHT", "UP", "DOWN"}) {
                String key = prefix + "face." + face;
                if (properties.containsKey(key)) {
                    faces.put(face, Boolean.parseBoolean(properties.getProperty(key)));
                }
            }

            BlockSettings entry = new BlockSettings(
                    blockId,
                    properties.getProperty(prefix + "movement", "VANILLA"),
                    properties.getProperty(prefix + "scheduledTicks", "VANILLA"),
                    clamp(readInt(properties, prefix + "pushCost", 1), 1, Integer.MAX_VALUE),
                    properties.getProperty(prefix + "adhesion", "VANILLA"),
                    properties.getProperty(prefix + "geometrySource", "OUTLINE"),
                    clamp(readInt(properties, prefix + "minimumSquare", 3), 1, 16),
                    clamp(readInt(properties, prefix + "depth", 2), 1, 16),
                    properties.getProperty(prefix + "orientation", "AUTO"),
                    properties.getProperty(prefix + "leadDirection", "AUTO"),
                    faces,
                    emptyToNull(properties.getProperty(prefix + "sourceEntryId", ""))
            );
            loaded.put(blockId, entry);
        }

        settings = immutableCopy(loaded);
    }

    /**
     * Atomically replaces the runtime cache file and this process's in-memory snapshot.
     * The caller supplies the already-resolved effective settings, in priority order.
     */
    public static synchronized void write(Path file, Map<String, BlockSettings> effectiveSettings)
            throws IOException {
        if (file == null) throw new IllegalArgumentException("Cache file path cannot be null");
        Map<String, BlockSettings> replacement = immutableCopy(effectiveSettings);

        Properties properties = new Properties();
        properties.setProperty("format.version", Integer.toString(FORMAT_VERSION));
        for (Map.Entry<String, BlockSettings> item : replacement.entrySet()) {
            BlockSettings entry = item.getValue();
            String encoded = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(entry.blockId().getBytes(StandardCharsets.UTF_8));
            String prefix = "block." + encoded + ".";
            properties.setProperty(prefix + "id", entry.blockId());
            properties.setProperty(prefix + "movement", entry.movement());
            properties.setProperty(prefix + "scheduledTicks", entry.scheduledTicks());
            properties.setProperty(prefix + "pushCost", Integer.toString(entry.pushCost()));
            properties.setProperty(prefix + "adhesion", entry.adhesion());
            properties.setProperty(prefix + "geometrySource", entry.geometrySource());
            properties.setProperty(prefix + "minimumSquare", Integer.toString(entry.minimumSquare()));
            properties.setProperty(prefix + "depth", Integer.toString(entry.depth()));
            properties.setProperty(prefix + "orientation", entry.orientation());
            properties.setProperty(prefix + "leadDirection", entry.leadDirection());
            if (entry.sourceEntryId() != null) {
                properties.setProperty(prefix + "sourceEntryId", entry.sourceEntryId());
            }
            entry.faces().forEach((face, enabled) ->
                    properties.setProperty(prefix + "face." + face, Boolean.toString(enabled)));
        }

        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try (OutputStream output = Files.newOutputStream(temporary)) {
            properties.store(output, "Movable Block Entities runtime config cache (format v1)");
        }
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }

        settings = replacement;
    }

    /** Returns custom settings, or the standard defaults when the block has no cache entry. */
    public static BlockSettings get(String blockId) {
        if (blockId == null || blockId.isBlank()) return BlockSettings.defaults("");
        return settings.getOrDefault(blockId, BlockSettings.defaults(blockId));
    }

    /** Returns only configured entries; callers can distinguish defaults from exported settings. */
    public static BlockSettings find(String blockId) {
        return settings.get(blockId);
    }

    /** Returns the current immutable runtime snapshot. */
    public static Map<String, BlockSettings> snapshot() {
        return settings;
    }

    private static Map<String, BlockSettings> immutableCopy(Map<String, BlockSettings> source) {
        if (source == null || source.isEmpty()) return Map.of();
        Map<String, BlockSettings> copy = new TreeMap<>();
        source.forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null
                    && key.equals(value.blockId())) {
                copy.put(key, value);
            }
        });
        return Collections.unmodifiableMap(new LinkedHashMap<>(copy));
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static int readInt(Properties properties, String key, int fallback) {
        try {
            return Integer.parseInt(properties.getProperty(key, Integer.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Stable, GUI-independent data contract consumed by runtime behavior implementations. */
    public record BlockSettings(
            String blockId,
            String movement,
            String scheduledTicks,
            int pushCost,
            String adhesion,
            String geometrySource,
            int minimumSquare,
            int depth,
            String orientation,
            String leadDirection,
            Map<String, Boolean> faces,
            String sourceEntryId
    ) {
        public BlockSettings {
            blockId = blockId == null ? "" : blockId;
            movement = movement == null ? "VANILLA" : movement;
            scheduledTicks = scheduledTicks == null ? "VANILLA" : scheduledTicks;
            pushCost = Math.max(1, pushCost);
            adhesion = adhesion == null ? "VANILLA" : adhesion;
            geometrySource = geometrySource == null ? "OUTLINE" : geometrySource;
            minimumSquare = clamp(minimumSquare, 1, 16);
            depth = clamp(depth, 1, 16);
            orientation = orientation == null ? "AUTO" : orientation;
            leadDirection = leadDirection == null ? "AUTO" : leadDirection;
            faces = faces == null ? Map.of() : Map.copyOf(faces);
        }

        public static BlockSettings defaults(String blockId) {
            return new BlockSettings(blockId, "VANILLA", "VANILLA", 1, "VANILLA",
                    "OUTLINE", 3, 2, "AUTO", "AUTO", Map.of(), null);
        }
    }
}
