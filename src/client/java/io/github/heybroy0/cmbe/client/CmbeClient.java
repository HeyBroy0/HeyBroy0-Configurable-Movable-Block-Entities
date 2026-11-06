package io.github.heybroy0.cmbe.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.heybroy0.cmbe.CmbeMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;

public final class CmbeClient implements ClientModInitializer {
    private static final KeyMapping.Category CATEGORY =
        KeyMapping.Category.register(CmbeMod.id("controls"));
    private static final KeyMapping OPEN_CONFIG = KeyMappingHelper.registerKeyMapping(
        new KeyMapping(
            "key.cmbe.open_config",
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_O,
            CATEGORY
        )
    );

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_CONFIG.consumeClick()) {
                Screen parent = client.gui.screen();
                client.gui.setScreen(new ConfigScreen(parent));
            }
        });
    }
}
