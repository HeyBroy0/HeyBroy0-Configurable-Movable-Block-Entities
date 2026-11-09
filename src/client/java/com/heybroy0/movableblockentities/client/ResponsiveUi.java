package com.heybroy0.movableblockentities.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

/** Shared transform and input mapping so each screen scales as one interactive layout. */
public final class ResponsiveUi {
    private static final float EDGE_PADDING = 8.0F;
    private static final float PREFERRED_SCALE = 0.75F;

    private ResponsiveUi() {}

    public static float scale(int screenWidth, int screenHeight, int baseWidth, int baseHeight) {
        float widthScale = Math.max(0.01F, (screenWidth - EDGE_PADDING * 2.0F) / baseWidth);
        float heightScale = Math.max(0.01F, (screenHeight - EDGE_PADDING * 2.0F) / baseHeight);
        return Math.min(PREFERRED_SCALE, Math.min(widthScale, heightScale));
    }

    public static float offsetX(int screenWidth, int screenHeight, int baseWidth, int baseHeight) {
        return (screenWidth - baseWidth * scale(screenWidth, screenHeight, baseWidth, baseHeight)) / 2.0F;
    }

    public static float offsetY(int screenWidth, int screenHeight, int baseWidth, int baseHeight) {
        return (screenHeight - baseHeight * scale(screenWidth, screenHeight, baseWidth, baseHeight)) / 2.0F;
    }

    public static void push(GuiGraphicsExtractor graphics, int screenWidth, int screenHeight,
                            int baseWidth, int baseHeight) {
        float scale = scale(screenWidth, screenHeight, baseWidth, baseHeight);
        graphics.pose().pushMatrix();
        graphics.pose().translate(
                offsetX(screenWidth, screenHeight, baseWidth, baseHeight),
                offsetY(screenWidth, screenHeight, baseWidth, baseHeight));
        graphics.pose().scale(scale, scale);
    }

    public static double toLogicalX(double mouseX, int screenWidth, int screenHeight,
                                    int baseWidth, int baseHeight) {
        // A small rightward correction compensates for the text-field hit/caret position
        // landing slightly left of the pointer after the scaled GUI transform.
        return (mouseX - offsetX(screenWidth, screenHeight, baseWidth, baseHeight))
                / scale(screenWidth, screenHeight, baseWidth, baseHeight) + 2.0D;
    }

    public static double toLogicalY(double mouseY, int screenWidth, int screenHeight,
                                    int baseWidth, int baseHeight) {
        return (mouseY - offsetY(screenWidth, screenHeight, baseWidth, baseHeight))
                / scale(screenWidth, screenHeight, baseWidth, baseHeight);
    }

    public static int toLogicalX(int mouseX, int screenWidth, int screenHeight,
                                 int baseWidth, int baseHeight) {
        return (int) Math.round(toLogicalX((double) mouseX, screenWidth, screenHeight, baseWidth, baseHeight));
    }

    public static int toLogicalY(int mouseY, int screenWidth, int screenHeight,
                                 int baseWidth, int baseHeight) {
        return (int) Math.round(toLogicalY((double) mouseY, screenWidth, screenHeight, baseWidth, baseHeight));
    }

    public static MouseButtonEvent toLogical(MouseButtonEvent event, int screenWidth, int screenHeight,
                                             int baseWidth, int baseHeight) {
        return new MouseButtonEvent(
                toLogicalX(event.x(), screenWidth, screenHeight, baseWidth, baseHeight),
                toLogicalY(event.y(), screenWidth, screenHeight, baseWidth, baseHeight),
                event.buttonInfo());
    }
}
