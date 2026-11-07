package io.github.heybroy0.cmbe;

import io.github.heybroy0.cmbe.config.ConfigManager;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ConfigurableMovableBlockEntities implements ModInitializer {
    public static final String MOD_ID = "cmbe";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ConfigManager.load();
        LOGGER.info("CMBE GUI Preview initialized; piston behavior is not installed.");
    }
}
