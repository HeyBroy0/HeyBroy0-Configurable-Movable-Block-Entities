package com.heybroy0.movableblockentities;

import com.heybroy0.movableblockentities.core.RuntimeConfigCache;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Server/common entrypoint. Runtime behavior is driven exclusively by the
 * flattened cache emitted by the GUI; the editable GUI tree is never parsed here.
 */
public final class MovableBlockEntities implements ModInitializer {
    public static final String MOD_ID = "movable_block_entities";

    @Override
    public void onInitialize() {
        Path cache = FabricLoader.getInstance().getConfigDir()
                .resolve("movable-block-entities")
                .resolve("cache")
                .resolve("runtime-config.properties");
        try {
            RuntimeConfigCache.load(cache);
            System.out.println("[Movable Block Entities] Loaded runtime settings from " + cache);
        } catch (IOException exception) {
            System.err.println("[Movable Block Entities] Could not load runtime settings from "
                    + cache + ": " + exception.getMessage());
        }
    }
}
