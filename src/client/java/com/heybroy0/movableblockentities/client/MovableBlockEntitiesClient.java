package com.heybroy0.movableblockentities.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public final class MovableBlockEntitiesClient implements ClientModInitializer {
    public static final String MOD_ID = "movable_block_entities";
    private static KeyMapping openConfiguration;

    public static String getOpenConfigurationKeyName() {
        return openConfiguration == null
                ? "O"
                : openConfiguration.getTranslatedKeyMessage().getString();
    }

    @Override
    public void onInitializeClient() {
        ConfigStore.load();
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "controls"));
        openConfiguration = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.movable_block_entities.open_config",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_O,
                category
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openConfiguration.consumeClick()) {
                if (client.gui.screen() == null) {
                    client.gui.setScreen(new MainConfigScreen());
                }
            }
        });
    }
}

