package com.heybroy0.movableblockentities.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Small scroll-by-page view of blocks with non-default saved GUI values. */
public final class ChangedBlocksScreen extends Screen {
    private static final int PANEL_WIDTH = 520;
    private static final int PANEL_HEIGHT = 350;
    private static final int PAGE_SIZE = 6;
    private final String returnBlock;
    private int page;
    private int left;
    private int top;
    private List<String> changed;

    public ChangedBlocksScreen(String returnBlock, int page) {
        super(Component.literal("Changed Blocks"));
        this.returnBlock = returnBlock;
        this.page = Math.max(0, page);
    }

    @Override
    protected void init() {
        clearWidgets();
        left = 0;
        top = 0;
        changed = ConfigStore.changedBlockIds();
        int pages = Math.max(1, (changed.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.min(page, pages - 1);

        int start = page * PAGE_SIZE;
        int end = Math.min(changed.size(), start + PAGE_SIZE);
        for (int i = start; i < end; i++) {
            String id = changed.get(i);
            int rowY = top + 72 + (i - start) * 36;
            addRenderableWidget(Button.builder(Component.literal("Focus"), button ->
                    minecraft.gui.setScreen(new MainConfigScreen(id))).bounds(left + 345, rowY, 72, 22).build());
            addRenderableWidget(Button.builder(Component.literal("Revert"), button -> {
                ConfigStore.revert(id);
                minecraft.gui.setScreen(new ChangedBlocksScreen(returnBlock, page));
            }).bounds(left + 425, rowY, 75, 22).build());
        }

        addRenderableWidget(Button.builder(Component.literal("‹ Previous"), button ->
                minecraft.gui.setScreen(new ChangedBlocksScreen(returnBlock, Math.max(0, page - 1))))
                .bounds(left + 20, top + PANEL_HEIGHT - 42, 105, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Next ›"), button ->
                minecraft.gui.setScreen(new ChangedBlocksScreen(returnBlock, page + 1)))
                .bounds(left + 135, top + PANEL_HEIGHT - 42, 90, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), button ->
                minecraft.gui.setScreen(new MainConfigScreen(returnBlock)))
                .bounds(left + PANEL_WIDTH - 130, top + PANEL_HEIGHT - 42, 110, 22).build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.fillGradient(0, 0, width, height, 0xFF202329, 0xFF101215);
        ResponsiveUi.push(graphics, width, height, PANEL_WIDTH, PANEL_HEIGHT);
        graphics.fill(left - 3, top - 3, left + PANEL_WIDTH + 3, top + PANEL_HEIGHT + 3, 0xAA050607);
        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xEE292D32);
        graphics.outline(left, top, PANEL_WIDTH, PANEL_HEIGHT, 0xFF555B62);
        graphics.fill(left + 1, top + 1, left + PANEL_WIDTH - 1, top + 38, 0xFF202428);
        graphics.pose().popMatrix();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        ResponsiveUi.push(graphics, width, height, PANEL_WIDTH, PANEL_HEIGHT);
        int logicalMouseX = ResponsiveUi.toLogicalX(mouseX, width, height, PANEL_WIDTH, PANEL_HEIGHT);
        int logicalMouseY = ResponsiveUi.toLogicalY(mouseY, width, height, PANEL_WIDTH, PANEL_HEIGHT);

        graphics.centeredText(font, "CHANGED BLOCKS", left + PANEL_WIDTH / 2, top + 14, 0xFFFFFFFF);
        graphics.text(font, "Only entries that differ from their effective defaults are listed.",
                left + 22, top + 48, 0xFFBBC3CC);
        if (changed.isEmpty()) {
            graphics.centeredText(font, "No changed blocks yet.", left + PANEL_WIDTH / 2, top + 160, 0xFF9FA8B2);
        } else {
            int start = page * PAGE_SIZE;
            int end = Math.min(changed.size(), start + PAGE_SIZE);
            for (int i = start; i < end; i++) {
                String id = changed.get(i);
                int rowY = top + 79 + (i - start) * 36;
                graphics.fill(left + 18, rowY - 5, left + PANEL_WIDTH - 18, rowY + 25, 0xFF23282E);
                graphics.text(font, id, left + 30, rowY + 4, 0xFFE5E8EB);
            }
            graphics.text(font, "Page " + (page + 1) + " / " + Math.max(1, (changed.size() + PAGE_SIZE - 1) / PAGE_SIZE),
                    left + 250, top + PANEL_HEIGHT - 35, 0xFF9FA8B2);
        }
        super.extractRenderState(graphics, logicalMouseX, logicalMouseY, a);
        graphics.pose().popMatrix();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return super.mouseClicked(
                ResponsiveUi.toLogical(event, width, height, PANEL_WIDTH, PANEL_HEIGHT), doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return super.mouseScrolled(
                ResponsiveUi.toLogicalX(mouseX, width, height, PANEL_WIDTH, PANEL_HEIGHT),
                ResponsiveUi.toLogicalY(mouseY, width, height, PANEL_WIDTH, PANEL_HEIGHT),
                scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}

