package io.github.heybroy0.cmbe.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.heybroy0.cmbe.ConfigurableMovableBlockEntities;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.TreeMap;

public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path settingsPath;
    private static ConfigFile config = new ConfigFile();

    private ConfigManager() {
    }

    public static synchronized void load() {
        Path directory = FabricLoader.getInstance().getConfigDir().resolve(ConfigurableMovableBlockEntities.MOD_ID);
        settingsPath = directory.resolve("settings.json");
        try {
            Files.createDirectories(directory);
            if (Files.exists(settingsPath)) {
                ConfigFile loaded = GSON.fromJson(Files.readString(settingsPath, StandardCharsets.UTF_8), ConfigFile.class);
                config = loaded == null ? new ConfigFile() : loaded;
            } else {
                config = new ConfigFile();
            }
            if (config.blocks == null) {
                config.blocks = new TreeMap<>();
            }
            Map<String, BlockSettings> sanitized = new TreeMap<>();
            config.blocks.forEach((id, settings) -> {
                if (id != null && !id.isBlank() && settings != null) {
                    BlockSettings normalized = settings.normalized();
                    if (!normalized.isDefault()) {
                        sanitized.put(id, normalized);
                    }
                }
            });
            config.blocks = sanitized;
            save();
        } catch (Exception exception) {
            ConfigurableMovableBlockEntities.LOGGER.error("Could not load block settings; defaults will be used.", exception);
            config = new ConfigFile();
        }
    }

    public static synchronized BlockSettings get(String blockId) {
        BlockSettings settings = config.blocks.get(blockId);
        return settings == null ? new BlockSettings() : settings.normalized();
    }

    public static synchronized void set(String blockId, BlockSettings settings) {
        if (blockId == null || blockId.isBlank() || settings == null) {
            return;
        }
        BlockSettings normalized = settings.normalized();
        if (normalized.isDefault()) {
            config.blocks.remove(blockId);
        } else {
            config.blocks.put(blockId, normalized);
        }
        save();
    }

    public static synchronized void revert(String blockId) {
        if (blockId != null) {
            config.blocks.remove(blockId);
            save();
        }
    }

    public static synchronized Map<String, BlockSettings> getChangedBlocks() {
        Map<String, BlockSettings> result = new TreeMap<>();
        config.blocks.forEach((id, settings) -> result.put(id, settings.copy()));
        return Map.copyOf(result);
    }

    public static synchronized void save() {
        if (settingsPath == null) {
            settingsPath = FabricLoader.getInstance().getConfigDir()
                .resolve(ConfigurableMovableBlockEntities.MOD_ID)
                .resolve("settings.json");
        }
        Path temporary = settingsPath.resolveSibling("settings.json.tmp");
        try {
            Files.createDirectories(settingsPath.getParent());
            Files.writeString(temporary, GSON.toJson(config), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, settingsPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, settingsPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            ConfigurableMovableBlockEntities.LOGGER.error("Could not save block settings to {}", settingsPath, exception);
        }
    }

    private static final class ConfigFile {
        private int schemaVersion = 1;
        private Map<String, BlockSettings> blocks = new TreeMap<>();
    }
}
