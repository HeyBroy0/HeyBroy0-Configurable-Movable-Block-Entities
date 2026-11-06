package io.github.heybroy0.cmbe.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.heybroy0.cmbe.adhesion.AdhesionAnalysisCache;
import io.github.heybroy0.cmbe.config.BlockConfig;
import io.github.heybroy0.cmbe.config.ConfigStore;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Direction;
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
    private int highlightedSuggestion;
    private int changedScroll;

    private int left;
    private int top;
    private final int panelWidth = 560;
    private final int panelHeight = 390;

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
            searchBox = new EditBox(font, left + 18, top + 91, 340, 20, Component.literal("Search blocks"));
            searchBox.setMaxLength(128);
            searchBox.setValue(search);
            searchBox.setResponder(value -> {
                search = value;
                suggestions = getSuggestions(value);
                highlightedSuggestion = 0;
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
        highlightedSuggestion = 0;
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
            case HITBOX -> drawHitboxPage(graphics);
            case ADVANCED -> drawAdvancedPage(graphics);
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
            graphics.fill(left + 18, suggestionTop, left + 358, suggestionTop + suggestions.size() * 18 + 2, 0xFF111419);
            graphics.outline(left + 18, suggestionTop, 340, suggestions.size() * 18 + 2, 0xFF697482);
            for (int i = 0; i < suggestions.size(); i++) {
                int rowY = suggestionTop + 2 + i * 18;
                if (i == highlightedSuggestion) {
                    graphics.fill(left + 20, rowY, left + 356, rowY + 18, 0xFF394552);
                }
                text(graphics, suggestions.get(i), left + 25, rowY + 5, 0xFFE1E5EA);
            }
        }

        BlockConfig c = config();
        int rowY = y + 48;
        row(graphics, "MOVEMENT", c.movement.name(), x, rowY, 500);
        row(graphics, "SCHEDULED TICKS", c.scheduledTicks.name(), x, rowY + 36, 500);
        row(graphics, "PUSH DENOMINATOR", "1 / " + c.pushDenominator, x, rowY + 72, 500);
        text(graphics, "Block cost = 1 / denominator. Default total piston budget: 12.", x, rowY + 108, 0xFF929BA7);
        text(graphics, "Only explicit per-block settings override vanilla behavior.", x, rowY + 128, 0xFF929BA7);
        text(graphics, "Use the tabs above to configure adhesion and geometry.", x, top + panelHeight - 28, 0xFFBFC7D2);
        text(graphics, "−", x + 452, rowY + 81, 0xFFFFFFFF);
        text(graphics, "+", x + 478, rowY + 81, 0xFFFFFFFF);
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
            graphics.fill(x, lineY, x + 510, lineY + 20, 0xFF2B3038);
            text(graphics, ids.get(i + changedScroll), x + 8, lineY + 6, 0xFFFFFFFF);
            text(graphics, "FOCUS", x + 405, lineY + 6, 0xFF91C9FF);
            text(graphics, "REVERT", x + 465, lineY + 6, 0xFFE5A4A4);
        }
    }

    private String displayAdhesion(BlockConfig.Adhesion adhesion) {
        return switch (adhesion) {
            case VANILLA -> "VANILLA";
            case ADHESIVE -> "ADHESIVE (ALL FACES)";
            case GLAZED -> "GLAZED (NO FACES)";
            case HITBOX -> "HITBOX";
            case ADVANCED -> "ADVANCED";
        };
    }

    private void drawAdhesionPage(GuiGraphicsExtractor graphics) {
        int x = contentX();
        int y = contentY();
        BlockConfig c = config();
        text(graphics, "ADHESION MODE FOR " + selectedBlock, x, y - 4, 0xFFBFC7D2);
        row(graphics, "MODE", displayAdhesion(c.adhesion), x, y + 28, 500);
        text(graphics, "VANILLA — preserve the block's normal sticky behavior.", x, y + 76, 0xFFE1E5EA);
        text(graphics, "ADHESIVE (ALL) — allow adhesion on all six faces.", x, y + 99, 0xFFE1E5EA);
        text(graphics, "GLAZED (NONE) — this block contributes no adhesive faces.", x, y + 122, 0xFFE1E5EA);
        text(graphics, "HITBOX — use cached outline/collision geometry per face.", x, y + 145, 0xFFE1E5EA);
        text(graphics, "ADVANCED — use the manual six-face configuration.", x, y + 168, 0xFFE1E5EA);
        if (c.adhesion == BlockConfig.Adhesion.HITBOX) {
            actionButton(graphics, "CONFIGURE HITBOX", x, y + 204, 172, 26, true);
        } else if (c.adhesion == BlockConfig.Adhesion.ADVANCED) {
            actionButton(graphics, "CONFIGURE ADVANCED", x, y + 204, 190, 26, true);
        }
    }

    private void drawHitboxPage(GuiGraphicsExtractor graphics) {
        int x = contentX();
        int y = contentY();
        BlockConfig c = config();
        text(graphics, "HITBOX ADHESION — " + selectedBlock, x, y - 4, 0xFFFFFFFF);
        text(graphics, "Measures the largest fully occupied square on each face.", x, y + 22, 0xFFBFC7D2);

        row(graphics, "GEOMETRY SOURCE", c.geometrySource.name(), x, y + 47, 500);
        drawSlider(graphics, "MINIMUM CONTACT SQUARE", c.hitboxMinimumSquare, 1, 16, x, y + 105);
        drawSlider(graphics, "INWARD DEPTH (VOXELS)", c.hitboxDepth, 1, 16, x, y + 169);

        text(graphics, "Default: 3 × 3 pixels, sampled 2 voxels inward.", x, y + 235, 0xFF929BA7);
        text(graphics, "Composter rims and narrow strips fail the 3 × 3 test.", x, y + 254, 0xFF929BA7);
        String status = AdhesionAnalysisCache.isTruncated(selectedBlock)
            ? "Analysis is bounded; some states require a custom adapter."
            : "Geometry measurements are cached before piston movement.";
        text(graphics, status, x, y + 286, 0xFFDBB978);
        text(graphics, "Detected geometry properties: " + String.join(", ", AdhesionAnalysisCache.geometryProperties(selectedBlock)), x, y + 306, 0xFFBFC7D2);
    }

    private void drawSlider(GuiGraphicsExtractor graphics, String label, int value, int min, int max, int x, int y) {
        text(graphics, label + ": " + value, x, y, 0xFFBFC7D2);
        int trackX = x;
        int trackY = y + 22;
        int trackWidth = 480;
        graphics.fill(trackX, trackY, trackX + trackWidth, trackY + 6, 0xFF454B54);
        int filled = (int) Math.round((value - min) / (double) (max - min) * trackWidth);
        graphics.fill(trackX, trackY, trackX + filled, trackY + 6, 0xFF8B929C);
        graphics.fill(trackX + filled - 3, trackY - 4, trackX + filled + 4, trackY + 10, 0xFFD8DEE7);
        graphics.outline(trackX, trackY - 4, trackWidth, 14, 0xFF606873);
        text(graphics, String.valueOf(min), trackX, trackY + 17, 0xFF929BA7);
        text(graphics, String.valueOf(max), trackX + trackWidth - 8, trackY + 17, 0xFF929BA7);
    }

    private void drawAdvancedPage(GuiGraphicsExtractor graphics) {
        int x = contentX();
        int y = contentY();
        BlockConfig c = config();
        text(graphics, "ADVANCED ADHESION — " + selectedBlock, x, y - 4, 0xFFFFFFFF);
        text(graphics, "Optional orientation property", x, y + 22, 0xFFBFC7D2);
        String orientation = c.orientationProperty.isBlank() ? "Absolute directions" : c.orientationProperty;
        row(graphics, "ORIENTATION", orientation, x, y + 42, 500);
        text(graphics, "Select a Direction-valued property, or leave unset for absolute faces.", x, y + 76, 0xFF929BA7);
        text(graphics, "Detected orientation properties: " + String.join(", ", AdhesionAnalysisCache.directionProperties(selectedBlock)), x, y + 95, 0xFF929BA7);

        text(graphics, "TOGGLE ADHESIVE FACES", x, y + 126, 0xFFBFC7D2);
        drawFaceTile(graphics, "U", c.manualUp, x + 90, y + 151);
        drawFaceTile(graphics, "L", c.manualLeft, x + 40, y + 199);
        drawFaceTile(graphics, "F", c.manualFront, x + 90, y + 199);
        drawFaceTile(graphics, "R", c.manualRight, x + 140, y + 199);
        drawFaceTile(graphics, "B", c.manualBack, x + 190, y + 199);
        drawFaceTile(graphics, "D", c.manualDown, x + 90, y + 247);
        text(graphics, "Green = adhesive; red = non-adhesive.", x + 270, y + 183, 0xFFBFC7D2);
        text(graphics, "Manual faces do not alter the block's real orientation or state.", x + 270, y + 205, 0xFF929BA7);
    }

    private void drawFaceTile(GuiGraphicsExtractor graphics, String label, boolean enabled, int x, int y) {
        int color = enabled ? 0xFF276B45 : 0xFF8B3434;
        graphics.fill(x, y, x + 44, y + 42, color);
        graphics.outline(x, y, 44, 42, 0xFFB9C0C9);
        centered(graphics, label, x + 22, y + 16, 0xFFFFFFFF);
    }

    private void actionButton(GuiGraphicsExtractor graphics, String label, int x, int y, int w, int h, boolean enabled) {
        graphics.fill(x, y, x + w, y + h, enabled ? 0xFF46515E : 0xFF30363E);
        graphics.outline(x, y, w, h, 0xFF697482);
        centered(graphics, label, x + w / 2, y + 8, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 1) return super.mouseClicked(event, doubleClick);
        int x = (int) event.x();
        int y = (int) event.y();
        if (clickTab(x, y)) return true;

        if (page == Page.BLOCK) {
            int suggestTop = top + 112;
            if (x >= left + 18 && x <= left + 358 && y >= suggestTop && y < suggestTop + suggestions.size() * 18 + 2) {
                int index = (y - suggestTop - 2) / 18;
                if (index >= 0 && index < suggestions.size()) selectBlock(suggestions.get(index));
                return true;
            }
            int rowX = contentX();
            int rowY = contentY() + 48;
            if (inside(x, y, rowX, rowY, 500, 27)) {
                change(c -> c.movement = BlockConfig.Movement.values()[(c.movement.ordinal() + 1) % BlockConfig.Movement.values().length]);
                return true;
            }
            if (inside(x, y, rowX, rowY + 36, 500, 27)) {
                change(c -> c.scheduledTicks = BlockConfig.ScheduledTicks.values()[(c.scheduledTicks.ordinal() + 1) % BlockConfig.ScheduledTicks.values().length]);
                return true;
            }
            if (inside(x, y, rowX + 448, rowY + 72, 24, 27)) {
                change(c -> c.pushDenominator = BlockConfig.sanitizeDenominator(c.pushDenominator - 1));
                return true;
            }
            if (inside(x, y, rowX + 474, rowY + 72, 24, 27)) {
                change(c -> c.pushDenominator = BlockConfig.sanitizeDenominator(c.pushDenominator + 1));
                return true;
            }
        } else if (page == Page.ADHESION) {
            int x0 = contentX();
            int y0 = contentY() + 28;
            if (inside(x, y, x0, y0, 500, 27)) {
                change(c -> c.adhesion = BlockConfig.Adhesion.values()[(c.adhesion.ordinal() + 1) % BlockConfig.Adhesion.values().length]);
                return true;
            }
            int buttonY = contentY() + 204;
            if (config().adhesion == BlockConfig.Adhesion.HITBOX && inside(x, y, x0, buttonY, 172, 26)) {
                page = Page.HITBOX;
                init();
                return true;
            }
            if (config().adhesion == BlockConfig.Adhesion.ADVANCED && inside(x, y, x0, buttonY, 190, 26)) {
                page = Page.ADVANCED;
                init();
                return true;
            }
        } else if (page == Page.HITBOX) {
            int x0 = contentX();
            int y0 = contentY();
            if (inside(x, y, x0, y0 + 47, 500, 27)) {
                change(c -> c.geometrySource = c.geometrySource == BlockConfig.GeometrySource.OUTLINE
                    ? BlockConfig.GeometrySource.COLLISION : BlockConfig.GeometrySource.OUTLINE);
                return true;
            }
            if (inside(x, y, x0, y0 + 127, 480, 6)) {
                int value = sliderValue(x, x0, 480);
                change(c -> c.hitboxMinimumSquare = value);
                return true;
            }
            if (inside(x, y, x0, y0 + 191, 480, 6)) {
                int value = sliderValue(x, x0, 480);
                change(c -> c.hitboxDepth = value);
                return true;
            }
        } else if (page == Page.ADVANCED) {
            int x0 = contentX();
            int y0 = contentY();
            if (inside(x, y, x0, y0 + 42, 500, 27)) {
                List<String> candidates = new ArrayList<>();
                candidates.add("");
                candidates.addAll(AdhesionAnalysisCache.directionProperties(selectedBlock));
                int current = candidates.indexOf(config().orientationProperty);
                String next = candidates.get((current + 1 + candidates.size()) % candidates.size());
                change(c -> c.orientationProperty = next);
                return true;
            }
            if (inside(x, y, x0 + 90, y0 + 151, 44, 42)) {
                change(c -> c.manualUp = !c.manualUp); return true;
            }
            if (inside(x, y, x0 + 40, y0 + 199, 44, 42)) {
                change(c -> c.manualLeft = !c.manualLeft); return true;
            }
            if (inside(x, y, x0 + 90, y0 + 199, 44, 42)) {
                change(c -> c.manualFront = !c.manualFront); return true;
            }
            if (inside(x, y, x0 + 140, y0 + 199, 44, 42)) {
                change(c -> c.manualRight = !c.manualRight); return true;
            }
            if (inside(x, y, x0 + 190, y0 + 199, 44, 42)) {
                change(c -> c.manualBack = !c.manualBack); return true;
            }
            if (inside(x, y, x0 + 90, y0 + 247, 44, 42)) {
                change(c -> c.manualDown = !c.manualDown); return true;
            }
        } else if (page == Page.CHANGED) {
            List<String> ids = new ArrayList<>(ConfigStore.snapshot().keySet());
            int start = contentY() + 24;
            for (int i = 0; i < Math.min(10, ids.size() - changedScroll); i++) {
                int rowY = start + i * 22;
                if (y >= rowY && y <= rowY + 20) {
                    String id = ids.get(i + changedScroll);
                    if (x >= contentX() + 400 && x < contentX() + 458) {
                        selectBlock(id);
                        return true;
                    }
                    if (x >= contentX() + 458 && x < contentX() + 520) {
                        ConfigStore.revert(id);
                        init();
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private int sliderValue(int mouseX, int trackX, int trackWidth) {
        double progress = Math.max(0.0, Math.min(1.0, (mouseX - trackX) / (double) trackWidth));
        return 1 + (int) Math.round(progress * 15);
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
        if (page == Page.BLOCK && searchBox != null && !suggestions.isEmpty()) {
            if (event.key() == InputConstants.KEY_UP) {
                highlightedSuggestion = Math.max(0, highlightedSuggestion - 1);
                return true;
            }
            if (event.key() == InputConstants.KEY_DOWN) {
                highlightedSuggestion = Math.min(suggestions.size() - 1, highlightedSuggestion + 1);
                return true;
            }
            if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_KP_ENTER) {
                String exact = suggestions.stream().filter(value -> value.equalsIgnoreCase(search.trim())).findFirst()
                    .orElse(suggestions.get(highlightedSuggestion));
                selectBlock(exact);
                return true;
            }
            if (event.key() == InputConstants.KEY_TAB) {
                search = suggestions.get(highlightedSuggestion);
                searchBox.setValue(search);
                return true;
            }
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
