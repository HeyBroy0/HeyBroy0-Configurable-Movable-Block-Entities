package com.heybroy0.movableblockentities.client;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/** Client-side persistence for the GUI prototype. No piston hooks read this yet. */
public final class ConfigStore {
    private static final Map<String, BlockConfig> BLOCKS = new TreeMap<>();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("movable-block-entities")
            .resolve("settings.properties");
    private static boolean loaded;

    private ConfigStore() {}

    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        if (!Files.exists(FILE)) return;

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(FILE)) {
            properties.load(input);
        } catch (IOException exception) {
            System.err.println("[Movable Block Entities] Could not read settings: " + exception.getMessage());
            return;
        }

        for (String property : properties.stringPropertyNames()) {
            if (!property.startsWith("block.") || !property.endsWith(".id")) continue;
            String encoded = property.substring("block.".length(), property.length() - ".id".length());
            String id = properties.getProperty(property, "");
            if (id.isBlank()) continue;

            String prefix = "block." + encoded + ".";
            BlockConfig config = new BlockConfig();
            config.movement = readEnum(properties, prefix + "movement", Movement.class, Movement.VANILLA);
            config.scheduledTicks = readEnum(properties, prefix + "scheduledTicks", ScheduledTicks.class, ScheduledTicks.VANILLA);
            config.pushCost = Math.max(1, readInt(properties, prefix + "pushCost", 1));
            config.adhesion = readEnum(properties, prefix + "adhesion", Adhesion.class, Adhesion.VANILLA);
            config.geometrySource = readEnum(properties, prefix + "geometrySource", GeometrySource.class, GeometrySource.OUTLINE);
            config.minimumSquare = clamp(readInt(properties, prefix + "minimumSquare", 3), 1, 16);
            config.depth = clamp(readInt(properties, prefix + "depth", 2), 1, 16);
            config.orientation = readEnum(properties, prefix + "orientation", Orientation.class, Orientation.AUTO);
            config.leadDirection = properties.getProperty(prefix + "leadDirection", "AUTO");
            for (Face face : Face.values()) {
                config.faces.put(face, Boolean.parseBoolean(properties.getProperty(prefix + "face." + face.name(), "false")));
            }
            BLOCKS.put(id, config);
        }
    }

    public static synchronized BlockConfig get(String blockId) {
        load();
        return BLOCKS.computeIfAbsent(blockId, ignored -> new BlockConfig());
    }

    public static synchronized List<String> changedBlockIds() {
        load();
        List<String> result = new ArrayList<>();
        BLOCKS.forEach((id, config) -> {
            if (config.isChanged()) result.add(id);
        });
        result.sort(Comparator.naturalOrder());
        return result;
    }

    public static synchronized void revert(String blockId) {
        load();
        BLOCKS.remove(blockId);
        save();
    }

    public static synchronized void save() {
        load();
        Properties properties = new Properties();
        BLOCKS.forEach((id, config) -> {
            if (!config.isChanged()) return;
            String encoded = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(id.getBytes(StandardCharsets.UTF_8));
            String prefix = "block." + encoded + ".";
            properties.setProperty(prefix + "id", id);
            properties.setProperty(prefix + "movement", config.movement.name());
            properties.setProperty(prefix + "scheduledTicks", config.scheduledTicks.name());
            properties.setProperty(prefix + "pushCost", Integer.toString(config.pushCost));
            properties.setProperty(prefix + "adhesion", config.adhesion.name());
            properties.setProperty(prefix + "geometrySource", config.geometrySource.name());
            properties.setProperty(prefix + "minimumSquare", Integer.toString(config.minimumSquare));
            properties.setProperty(prefix + "depth", Integer.toString(config.depth));
            properties.setProperty(prefix + "orientation", config.orientation.name());
            properties.setProperty(prefix + "leadDirection", config.leadDirection);
            config.faces.forEach((face, enabled) -> properties.setProperty(prefix + "face." + face.name(), Boolean.toString(enabled)));
        });

        try {
            Files.createDirectories(FILE.getParent());
            Path temporary = FILE.resolveSibling(FILE.getFileName() + ".tmp");
            try (OutputStream output = Files.newOutputStream(temporary)) {
                properties.store(output, "Configurable Movable Block Entities - GUI prototype");
            }
            try {
                Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            System.err.println("[Movable Block Entities] Could not save settings: " + exception.getMessage());
        }
    }

    private static int readInt(Properties properties, String key, int fallback) {
        try { return Integer.parseInt(properties.getProperty(key, Integer.toString(fallback))); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private static <T extends Enum<T>> T readEnum(Properties properties, String key, Class<T> type, T fallback) {
        try { return Enum.valueOf(type, properties.getProperty(key, fallback.name()).toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return fallback; }
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }

    public enum Movement { VANILLA, MOVABLE, BREAKABLE, IMMOVABLE }
    public enum ScheduledTicks { VANILLA, TRANSFER, DUPLICATE }
    public enum Adhesion { VANILLA, ADHESIVE, GLAZED, HITBOX, ADVANCED }
    public enum GeometrySource { OUTLINE, COLLISION, VISUAL }
    public enum Orientation { AUTO, MANUAL, OMNIDIRECTIONAL }
    public enum Face { FRONT, BACK, LEFT, RIGHT, UP, DOWN }

    public static final class BlockConfig {
        public Movement movement = Movement.VANILLA;
        public ScheduledTicks scheduledTicks = ScheduledTicks.VANILLA;
        public int pushCost = 1;
        public Adhesion adhesion = Adhesion.VANILLA;
        public GeometrySource geometrySource = GeometrySource.OUTLINE;
        public int minimumSquare = 3;
        public int depth = 2;
        public Orientation orientation = Orientation.AUTO;
        public String leadDirection = "AUTO";
        public final Map<Face, Boolean> faces = new TreeMap<>();

        public BlockConfig() {
            for (Face face : Face.values()) faces.put(face, false);
        }

        public boolean isChanged() {
            if (movement != Movement.VANILLA || scheduledTicks != ScheduledTicks.VANILLA || pushCost != 1
                    || adhesion != Adhesion.VANILLA || geometrySource != GeometrySource.OUTLINE
                    || minimumSquare != 3 || depth != 2 || orientation != Orientation.AUTO
                    || !"AUTO".equals(leadDirection)) return true;
            return faces.values().stream().anyMatch(Boolean.TRUE::equals);
        }
    }
}

