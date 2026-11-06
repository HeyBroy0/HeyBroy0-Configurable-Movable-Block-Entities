package io.github.heybroy0.cmbe.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.heybroy0.cmbe.CmbeMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

public final class ConfigStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("cmbe/settings.json");
    private static final Map<String, BlockConfig> BLOCKS = new LinkedHashMap<>();
    private static boolean loaded;

    private ConfigStore() {}

    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        if (!Files.exists(FILE)) return;
        try (BufferedReader reader = Files.newBufferedReader(FILE)) {
            SettingsFile data = GSON.fromJson(reader, SettingsFile.class);
            if (data != null && data.blocks != null) {
                data.blocks.forEach((id, value) -> {
                    if (id != null && value != null) {
                        BlockConfig safe = value.copy();
                        if (!safe.isDefault()) BLOCKS.put(id, safe);
                    }
                });
            }
        } catch (Exception exception) {
            CmbeMod.LOGGER.error("Could not load block settings from {}. Keeping the file intact.", FILE, exception);
        }
    }

    public static synchronized BlockConfig get(String blockId) {
        load();
        BlockConfig value = BLOCKS.get(blockId);
        return value == null ? new BlockConfig() : value.copy();
    }

    public static BlockConfig get(BlockState state) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return get(id.toString());
    }

    public static synchronized void set(String blockId, BlockConfig value) {
        load();
        BlockConfig safe = value.copy();
        if (safe.isDefault()) BLOCKS.remove(blockId);
        else BLOCKS.put(blockId, safe);
        save();
    }

    public static synchronized void revert(String blockId) {
        load();
        if (BLOCKS.remove(blockId) != null) save();
    }

    public static synchronized Map<String, BlockConfig> snapshot() {
        load();
        Map<String, BlockConfig> result = new TreeMap<>();
        BLOCKS.forEach((id, config) -> result.put(id, config.copy()));
        return result;
    }

    public static synchronized void save() {
        load();
        try {
            Files.createDirectories(FILE.getParent());
            Path temporary = FILE.resolveSibling(FILE.getFileName() + ".tmp");
            SettingsFile data = new SettingsFile();
            data.schemaVersion = 1;
            data.blocks = snapshot();
            try (BufferedWriter writer = Files.newBufferedWriter(temporary)) {
                GSON.toJson(data, writer);
            }
            try {
                Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException unsupportedAtomicMove) {
                Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            CmbeMod.LOGGER.error("Could not save block settings to {}.", FILE, exception);
        }
    }

    private static final class SettingsFile {
        int schemaVersion = 1;
        Map<String, BlockConfig> blocks = new LinkedHashMap<>();
    }
}
