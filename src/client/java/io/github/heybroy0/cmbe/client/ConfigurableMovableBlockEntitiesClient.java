package io.github.heybroy0.cmbe.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.heybroy0.cmbe.ConfigurableMovableBlockEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public final class ConfigurableMovableBlockEntitiesClient implements ClientModInitializer {
    private static KeyMapping openConfig;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(ConfigurableMovableBlockEntities.MOD_ID, "main")
        );
        openConfig = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.cmbe.open_config",
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_O,
            category
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openConfig.consumeClick()) {
                client.gui.setScreen(new MovableBlockConfigScreen(client.gui.screen()));
            }
        });
    }
}
