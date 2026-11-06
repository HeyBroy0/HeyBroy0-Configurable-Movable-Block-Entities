package io.github.heybroy0.cmbe;

import io.github.heybroy0.cmbe.adhesion.AdhesionAnalysisCache;
import io.github.heybroy0.cmbe.config.ConfigStore;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CmbeMod implements ModInitializer {
    public static final String MOD_ID = "cmbe";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ConfigStore.load();
        ServerTickEvents.END_SERVER_TICK.register(server ->
            AdhesionAnalysisCache.analyzeRegisteredBlocks(2)
        );
        LOGGER.info("Configurable Movable Block Entities initialized.");
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
