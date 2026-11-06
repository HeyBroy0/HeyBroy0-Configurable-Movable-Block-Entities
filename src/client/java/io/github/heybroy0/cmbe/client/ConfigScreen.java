package io.github.heybroy0.cmbe.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.heybroy0.cmbe.config.BlockConfig;
import io.github.heybroy0.cmbe.config.ConfigStore;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ConfigScreen extends Screen {
    private enum Page { BLOCK, CHANGED, ADHESION, HITBOX, ADVANCED }

    private final Screen parent;
    private Page page = Page.BLOCK;
    private String selectedBlock = "minecraft:stone";
    private String search = "";
    private EditBox searchBox;
    private List<String> suggestions = List.of();
    private int changedScroll;

    private int left;
    private int top;
    private final int panelWidth = 540;
    private final int panelHeight = 380;

    public ConfigScreen(Screen parent) {
        super(Component.literal("Configurable Movable Block Entities"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        left = Math.max(8, (width - panelWidth) / 2);
        top = Math.max(8, (height - panelHeight) / 2);
        clearWidgets();

        if (page == Page.BLOCK) {
            searchBox = new EditBox(font, left + 18, top + 91, 330, 20, Component.literal("Search blocks"));
            searchBox.setMaxLength(128);
            searchBox.setValue(search);
            searchBox.setResponder(value -> {
                search = value;
                suggestions = getSuggestions(value);
            });
            addRenderableWidget(searchBox);
        } else {
            searchBox = null;
        }
    }

    private List<String> getSuggestions(String query) {
        String needle = query.trim().toLowerCase();
        if (needle.isEmpty()) return List.of();

        List<String> results = new ArrayList<>(8);
        for (Map.Entry<Identifier, Block> entry : BuiltInRegistries.BLOCK.entrySet()) {
            String id = entry.getKey().toString();
            String displayName = entry.getValue().getName().getString().toLowerCase();
            if (id.contains(needle) || displayName.contains(needle)) {
                results.add(id);
                if (results.size() >= 8) break;
            }
        }
        return results;
    }

    private BlockConfig config() {
        return ConfigStore.get(selectedBlock);
    }

    private void change(java.util.function.Consumer<BlockConfig> edit) {
        BlockConfig updated = config();
        edit.accept(updated);
        ConfigStore.set(selectedBlock, updated);
        init();
    }

    private void selectBlock(String id) {
        selectedBlock = id;
        search = id;
        page = Page.BLOCK;
        init();
    }

    private int contentX() { return left + 20; }
    private int contentY() { return top + 130; }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, width, height, 0xB9000000);
        graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xF0191C21);
        graphics.outline(left, top, panelWidth, panelHeight, 0xFF59616D);
        graphics.fill(left, top, left + panelWidth, top + 32, 0xFF252A31);
        text(graphics, "CONFIGURABLE MOVABLE BLOCK ENTITIES", left + 16, top + 11, 0xFFFFFFFF);
        drawTabs(graphics);

        switch (page) {
            case BLOCK -> drawBlockPage(graphics);
            case CHANGED -> drawChangedPage(graphics);
            case ADHESION -> drawAdhesionPage(graphics);
            case HITBOX -> drawPlaceholder(graphics, "HITBOX ANALYSIS", "Analysis controls are a non-functional template.", "Minimum contact square", "3 x 3 pixels", "Inward sample depth", "2 voxels");
            case ADVANCED -> drawPlaceholder(graphics, "ADVANCED ADHESION", "Advanced editor controls are a non-functional template.", "Geometry source", "Outline / selection shape", "Face overrides", "F / B / L / R / U / D");
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void text(GuiGraphicsExtractor graphics, String value, int x, int y, int color) {
        graphics.text(font, Component.literal(value), x, y, color, false);
    }

    private void centered(GuiGraphicsExtractor graphics, String value, int x, int y, int color) {
        graphics.text(font, Component.literal(value), x - font.width(value) / 2, y, color, false);
    }

    private void drawTabs(GuiGraphicsExtractor graphics) {
        tab(graphics, "BLOCK", Page.BLOCK, left + 16, top + 43, 76);
        tab(graphics, "CHANGED", Page.CHANGED, left + 98, top + 43, 88);
        tab(graphics, "ADHESION", Page.ADHESION, left + 192, top + 43, 88);
        tab(graphics, "HITBOX", Page.HITBOX, left + 286, top + 43, 76);
        tab(graphics, "ADVANCED", Page.ADVANCED, left + 368, top + 43, 98);
    }

    private void tab(GuiGraphicsExtractor graphics, String label, Page target, int x, int y, int w) {
        graphics.fill(x, y, x + w, y + 23, page == target ? 0xFF46515E : 0xFF30363E);
        graphics.outline(x, y, w, 23, 0xFF697482);
        centered(graphics, label, x + w / 2, y + 7, 0xFFFFFFFF);
    }

    private void row(GuiGraphicsExtractor graphics, String label, String value, int x, int y, int w) {
        graphics.fill(x, y, x + w, y + 27, 0xFF2B3038);
        graphics.outline(x, y, w, 27, 0xFF4A535F);
        text(graphics, label, x + 10, y + 9, 0xFFBFC7D2);
        text(graphics, value, x + 258, y + 9, 0xFFFFFFFF);
    }

    private void drawBlockPage(GuiGraphicsExtractor graphics) {
        int x = contentX();
        int y = contentY();
        text(graphics, "SEARCH BY REGISTRY ID OR DISPLAY NAME", x, y - 24, 0xFFBFC7D2);
        text(graphics, "Selected: " + selectedBlock, x, y + 12, 0xFFFFFFFF);

        if (!suggestions.isEmpty()) {
            int suggestionTop = top + 112;
            graphics.fill(left + 18, suggestionTop, left + 350, suggestionTop + suggestions.size() * 18 + 2, 0xFF111419);
            graphics.outline(left + 18, suggestionTop, 332, suggestions.size() * 18 + 2, 0xFF697482);
            for (int i = 0; i < suggestions.size(); i++) {
                text(graphics, suggestions.get(i), left + 25, suggestionTop + 6 + i * 18, 0xFFE1E5EA);
            }
        }

        BlockConfig c = config();
        int rowY = y + 48;
        row(graphics, "MOVEMENT", c.movement.name(), x, rowY, 490);
        row(graphics, "SCHEDULED TICKS", c.scheduledTicks.name(), x, rowY + 36, 490);
        row(graphics, "PUSH DENOMINATOR", "1 / " + c.pushDenominator, x, rowY + 72, 490);
        text(graphics, "One block costs 1 / denominator. Default total piston budget: 12.", x, rowY + 106, 0xFF929BA7);
        text(graphics, "Use the +/- controls at the right of the push denominator row.", x, rowY + 124, 0xFF929BA7);
        text(graphics, "Hitbox and Advanced are display-only placeholders.", x, top + panelHeight - 30, 0xFFDBB978);
        text(graphics, "-", x + 450, rowY + 81, 0xFFFFFFFF);
        text(graphics, "+", x + 474, rowY + 81, 0xFFFFFFFF);
    }

    private void drawChangedPage(GuiGraphicsExtractor graphics) {
        int x = contentX();
        int y = contentY();
        text(graphics, "BLOCKS WITH NON-DEFAULT SETTINGS", x, y - 4, 0xFFBFC7D2);
        List<String> ids = new ArrayList<>(ConfigStore.snapshot().keySet());
        if (ids.isEmpty()) {
            text(graphics, "No custom block settings.", x, y + 28, 0xFFFFFFFF);
            return;
        }
        int visible = Math.min(10, ids.size() - changedScroll);
        for (int i = 0; i < visible; i++) {
            int lineY = y + 24 + i * 22;
            graphics.fill(x, lineY, x + 500, lineY + 20, 0xFF2B3038);
            text(graphics, ids.get(i + changedScroll), x + 8, lineY + 6, 0xFFFFFFFF);
            text(graphics, "FOCUS", x + 405, lineY + 6, 0xFF91C9FF);
            text(graphics, "REVERT", x + 455, lineY + 6, 0xFFE5A4A4);
        }
    }

    private void drawAdhesionPage(GuiGraphicsExtractor graphics) {
        int x = contentX();
        int y = contentY();
        text(graphics, "ADHESION MODE FOR " + selectedBlock, x, y - 4, 0xFFBFC7D2);
        row(graphics, "MODE", config().adhesion.name(), x, y + 28, 490);
        text(graphics, "VANILLA — preserve the block's normal sticky behavior.", x, y + 76, 0xFFE1E5EA);
        text(graphics, "ALL — allow adhesion on all six faces.", x, y + 99, 0xFFE1E5EA);
        text(graphics, "NONE — disable adhesion on all six faces.", x, y + 122, 0xFFE1E5EA);
        text(graphics, "HITBOX / ADVANCED currently fall back to vanilla.", x, y + 159, 0xFFDBB978);
    }

    private void drawPlaceholder(GuiGraphicsExtractor graphics, String heading, String description,
                                 String a, String av, String b, String bv) {
        int x = contentX();
        int y = contentY();
        text(graphics, heading, x, y - 4, 0xFFFFFFFF);
        text(graphics, description, x, y + 26, 0xFFBFC7D2);
        placeholderSlider(graphics, a, av, x, y + 75);
        placeholderSlider(graphics, b, bv, x, y + 145);
        text(graphics, "UI only — values are not applied to block behavior.", x, y + 218, 0xFFDBB978);
    }

    private void placeholderSlider(GuiGraphicsExtractor graphics, String label, String value, int x, int y) {
        text(graphics, label + ": " + value, x, y, 0xFFBFC7D2);
        graphics.fill(x, y + 24, x + 440, y + 30, 0xFF454B54);
        graphics.fill(x, y + 24, x + 118, y + 30, 0xFF8B929C);
        graphics.outline(x, y + 20, 440, 14, 0xFF606873);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 1) return super.mouseClicked(event, doubleClick);
        int x = (int) event.x();
        int y = (int) event.y();
        if (clickTab(x, y)) return true;

        if (page == Page.BLOCK) {
            int suggestTop = top + 112;
            if (x >= left + 18 && x <= left + 350 && y >= suggestTop && y < suggestTop + suggestions.size() * 18 + 2) {
                int index = (y - suggestTop - 2) / 18;
                if (index >= 0 && index < suggestions.size()) selectBlock(suggestions.get(index));
                return true;
            }
            int rowX = contentX();
            int rowY = contentY() + 48;
            if (inside(x, y, rowX, rowY, 490, 27)) {
                change(c -> c.movement = BlockConfig.Movement.values()[(c.movement.ordinal() + 1) % BlockConfig.Movement.values().length]);
                return true;
            }
            if (inside(x, y, rowX, rowY + 36, 490, 27)) {
                change(c -> c.scheduledTicks = BlockConfig.ScheduledTicks.values()[(c.scheduledTicks.ordinal() + 1) % BlockConfig.ScheduledTicks.values().length]);
                return true;
            }
            if (inside(x, y, rowX + 444, rowY + 72, 22, 27)) {
                change(c -> c.pushDenominator = BlockConfig.sanitizeDenominator(c.pushDenominator - 1));
                return true;
            }
            if (inside(x, y, rowX + 468, rowY + 72, 22, 27)) {
                change(c -> c.pushDenominator = BlockConfig.sanitizeDenominator(c.pushDenominator + 1));
                return true;
            }
        } else if (page == Page.ADHESION) {
            int x0 = contentX();
            int y0 = contentY() + 28;
            if (inside(x, y, x0, y0, 490, 27)) {
                change(c -> c.adhesion = BlockConfig.Adhesion.values()[(c.adhesion.ordinal() + 1) % BlockConfig.Adhesion.values().length]);
                return true;
            }
        } else if (page == Page.CHANGED) {
            List<String> ids = new ArrayList<>(ConfigStore.snapshot().keySet());
            int start = contentY() + 24;
            for (int i = 0; i < Math.min(10, ids.size() - changedScroll); i++) {
                int rowY = start + i * 22;
                if (y >= rowY && y <= rowY + 20) {
                    String id = ids.get(i + changedScroll);
                    if (x >= contentX() + 400 && x < contentX() + 452) {
                        selectBlock(id);
                        page = Page.BLOCK;
                        init();
                        return true;
                    }
                    if (x >= contentX() + 452 && x < contentX() + 510) {
                        ConfigStore.revert(id);
                        init();
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private boolean clickTab(int x, int y) {
        int tabY = top + 43;
        if (y < tabY || y > tabY + 23) return false;
        if (inside(x, y, left + 16, tabY, 76, 23)) page = Page.BLOCK;
        else if (inside(x, y, left + 98, tabY, 88, 23)) page = Page.CHANGED;
        else if (inside(x, y, left + 192, tabY, 88, 23)) page = Page.ADHESION;
        else if (inside(x, y, left + 286, tabY, 76, 23)) page = Page.HITBOX;
        else if (inside(x, y, left + 368, tabY, 98, 23)) page = Page.ADVANCED;
        else return false;
        changedScroll = 0;
        init();
        return true;
    }

    private static boolean inside(int x, int y, int left, int top, int width, int height) {
        return x >= left && x <= left + width && y >= top && y <= top + height;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (page == Page.CHANGED) {
            int max = Math.max(0, ConfigStore.snapshot().size() - 10);
            changedScroll = Math.max(0, Math.min(max, changedScroll + (verticalAmount < 0 ? 1 : -1)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (page == Page.BLOCK && searchBox != null
            && (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_KP_ENTER)
            && !suggestions.isEmpty()) {
            selectBlock(suggestions.getFirst());
            return true;
        }
        if (page == Page.BLOCK && event.key() == InputConstants.KEY_TAB && !suggestions.isEmpty()) {
            search = suggestions.getFirst();
            if (searchBox != null) searchBox.setValue(search);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
