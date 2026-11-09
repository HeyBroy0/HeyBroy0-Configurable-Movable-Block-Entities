package com.heybroy0.movableblockentities.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static com.heybroy0.movableblockentities.client.ConfigStore.*;

/** Main page: all per-block settings stay visible on this screen. */
public final class MainConfigScreen extends Screen {
    private static final int PANEL_WIDTH = 520;
    private static final int PANEL_HEIGHT = 360;
    private static final int SUGGESTION_ROW_HEIGHT = 18;
    private static final int MAX_VISIBLE_SUGGESTIONS = 7;

    private String selectedBlockId;
    private BlockConfig config;
    private EditBox searchBox;
    private EditBox pushCostBox;
    private Button movementButton;
    private Button tickButton;
    private Button adhesionButton;
    private Button configureButton;
    private List<BlockSuggestion> suggestions = List.of();
    private int suggestionIndex;
    private int suggestionScrollOffset;
    private int left;
    private int top;
    private int searchY;

    public MainConfigScreen(String selectedBlockId) {
        super(Component.literal("Configurable Movable Block Entities"));
        this.selectedBlockId = selectedBlockId == null || selectedBlockId.isBlank()
                ? "minecraft:stone" : selectedBlockId;
        this.config = ConfigStore.get(this.selectedBlockId);
    }

    @Override
    protected void init() {
        clearWidgets();
        left = 0;
        top = 0;

        searchY = top + 66;
        searchBox = new EditBox(font, left + 20, searchY, PANEL_WIDTH - 40, 20,
                Component.literal("Block search"));
        searchBox.setMaxLength(160);
        searchBox.setBordered(true);
        searchBox.setHint(Component.literal("minecraft:stone or a block name"));
        searchBox.setValue(selectedBlockId);
        searchBox.setResponder(this::refreshSuggestions);
        addRenderableWidget(searchBox);

        int labelX = left + 22;
        int controlX = left + 205;
        int controlWidth = 180;
        int row1 = top + 116;
        int row2 = top + 151;
        int row3 = top + 186;
        int row4 = top + 221;

        movementButton = addButton("", controlX, row1, controlWidth, 22, button -> cycleMovement());
        tickButton = addButton("", controlX, row2, controlWidth, 22, button -> cycleTicks());
        pushCostBox = new EditBox(font, controlX, row3, 78, 22, Component.literal("Push cost denominator"));
        pushCostBox.setMaxLength(6);
        pushCostBox.setValue(Integer.toString(config.pushCost));
        pushCostBox.setResponder(value -> {
            try {
                int parsed = Integer.parseInt(value);
                if (parsed > 0) {
                    config.pushCost = parsed;
                    ConfigStore.save();
                }
            } catch (NumberFormatException ignored) {
                // Keep the last valid saved value while the user edits the field.
            }
        });
        addRenderableWidget(pushCostBox);

        adhesionButton = addButton("", controlX, row4, 145, 22, button -> cycleAdhesion());
        configureButton = addButton("Configure", controlX + 151, row4, 104, 22,
                button -> openAdhesionEditor());

        addButton("Changed blocks", left + 20, top + 307, 190, 22,
                button -> minecraft.gui.setScreen(new ChangedBlocksScreen(selectedBlockId, 0)));
        addButton("Done", left + PANEL_WIDTH - 135, top + 307, 115, 22,
                button -> minecraft.gui.setScreen(null));

        refreshControlLabels();
        searchBox.setFocused(true);
        setInitialFocus(searchBox);
    }

    private Button addButton(String label, int x, int y, int w, int h, Button.OnPress action) {
        return addRenderableWidget(Button.builder(Component.literal(label), action).bounds(x, y, w, h).build());
    }

    private void cycleMovement() {
        Movement[] values = Movement.values();
        config.movement = values[(config.movement.ordinal() + 1) % values.length];
        persistAndRefresh();
    }

    private void cycleTicks() {
        ScheduledTicks[] values = ScheduledTicks.values();
        config.scheduledTicks = values[(config.scheduledTicks.ordinal() + 1) % values.length];
        persistAndRefresh();
    }

    private void cycleAdhesion() {
        Adhesion[] values = Adhesion.values();
        config.adhesion = values[(config.adhesion.ordinal() + 1) % values.length];
        persistAndRefresh();
    }

    private void persistAndRefresh() {
        ConfigStore.save();
        refreshControlLabels();
    }

    private void refreshControlLabels() {
        if (movementButton == null) return;
        movementButton.setMessage(Component.literal("‹  " + pretty(config.movement.name()) + "  ›"));
        tickButton.setMessage(Component.literal("‹  " + pretty(config.scheduledTicks.name()) + "  ›"));
        adhesionButton.setMessage(Component.literal("‹  " + pretty(config.adhesion.name()) + "  ›"));
        boolean hasEditor = config.adhesion == Adhesion.HITBOX || config.adhesion == Adhesion.ADVANCED;
        configureButton.visible = hasEditor;
        configureButton.active = hasEditor;
        configureButton.setMessage(Component.literal(config.adhesion == Adhesion.HITBOX ? "Hitbox settings" : "Advanced settings"));
    }

    private static String pretty(String value) {
        String lower = value.toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder formatted = new StringBuilder();
        for (String word : lower.split(" ")) {
            if (formatted.length() > 0) formatted.append(' ');
            if (!word.isEmpty()) formatted.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return formatted.toString();
    }

    private void refreshSuggestions(String value) {
        suggestionIndex = 0;
        String query = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            suggestions = List.of();
            suggestionScrollOffset = 0;
            return;
        }

        List<BlockSuggestion> found = new ArrayList<>();
        for (var entry : BuiltInRegistries.BLOCK.entrySet()) {
            String id = entry.getKey().identifier().toString();
            String name = entry.getValue().getName().getString();
            String normalizedName = name.toLowerCase(Locale.ROOT);
            String path = entry.getKey().identifier().getPath().toLowerCase(Locale.ROOT);
            if (id.toLowerCase(Locale.ROOT).startsWith(query)
                    || path.startsWith(query)
                    || normalizedName.startsWith(query)
                    || id.toLowerCase(Locale.ROOT).contains(query)
                    || normalizedName.contains(query)) {
                found.add(new BlockSuggestion(id, name,
                        id.toLowerCase(Locale.ROOT).startsWith(query) || path.startsWith(query)));
            }
        }
        found.sort(Comparator.comparing(BlockSuggestion::prefixMatch).reversed()
                .thenComparing(BlockSuggestion::id));
        suggestions = List.copyOf(found);
        suggestionScrollOffset = 0;
        suggestionIndex = 0;
    }

    private boolean suggestionsOpen() {
        return searchBox != null && searchBox.isFocused() && !searchBox.getValue().isBlank() && !suggestions.isEmpty();
    }

    private int popupX() { return left + 20; }
    private int popupY() { return searchY + 22; }

    private void moveSuggestion(int direction, boolean wrap) {
        if (suggestions.isEmpty()) return;

        if (wrap) {
            suggestionIndex = Math.floorMod(suggestionIndex + direction, suggestions.size());
        } else {
            suggestionIndex = Math.max(0, Math.min(suggestions.size() - 1, suggestionIndex + direction));
        }

        if (suggestionIndex < suggestionScrollOffset) {
            suggestionScrollOffset = suggestionIndex;
        } else if (suggestionIndex >= suggestionScrollOffset + MAX_VISIBLE_SUGGESTIONS) {
            suggestionScrollOffset = suggestionIndex - MAX_VISIBLE_SUGGESTIONS + 1;
        }

        int maxOffset = Math.max(0, suggestions.size() - MAX_VISIBLE_SUGGESTIONS);
        suggestionScrollOffset = Math.max(0, Math.min(maxOffset, suggestionScrollOffset));
    }

    private void chooseSuggestion(int index) {
        if (index < 0 || index >= suggestions.size()) return;
        chooseBlock(suggestions.get(index).id());
    }

    private void chooseExactMatch() {
        String query = searchBox.getValue().trim();
        Identifier id = Identifier.tryParse(query);
        if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) {
            chooseBlock(id.toString());
            return;
        }
        for (BlockSuggestion suggestion : suggestions) {
            if (suggestion.id().equalsIgnoreCase(query) || suggestion.name().equalsIgnoreCase(query)) {
                chooseBlock(suggestion.id());
                return;
            }
        }
    }

    private void chooseBlock(String id) {
        selectedBlockId = id;
        config = ConfigStore.get(id);
        searchBox.setValue(id);
        searchBox.setFocused(false);
        suggestions = List.of();
        pushCostBox.setValue(Integer.toString(config.pushCost));
        refreshControlLabels();
    }

    private void openAdhesionEditor() {
        AdhesionEditorScreen.Page page = config.adhesion == Adhesion.HITBOX
                ? AdhesionEditorScreen.Page.HITBOX : AdhesionEditorScreen.Page.ADVANCED;
        minecraft.gui.setScreen(new AdhesionEditorScreen(selectedBlockId, page));
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (searchBox != null && searchBox.isFocused() && suggestionsOpen()) {
            if (event.key() == InputConstants.KEY_TAB) {
                moveSuggestion(event.hasShiftDown() ? -1 : 1, true);
                return true;
            }
            if (event.key() == InputConstants.KEY_DOWN) {
                moveSuggestion(1, true);
                return true;
            }
            if (event.key() == InputConstants.KEY_UP) {
                moveSuggestion(-1, true);
                return true;
            }
            if (event.key() == InputConstants.KEY_RETURN) {
                chooseSuggestion(suggestionIndex);
                return true;
            }
        } else if (searchBox != null && searchBox.isFocused()
                && event.key() == InputConstants.KEY_RETURN) {
            chooseExactMatch();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        MouseButtonEvent logicalEvent = ResponsiveUi.toLogical(event, width, height, PANEL_WIDTH, PANEL_HEIGHT);
        if (logicalEvent.button() == 1 && suggestionsOpen()) {
            int x = (int) logicalEvent.x();
            int y = (int) logicalEvent.y();
            int row = (y - popupY()) / SUGGESTION_ROW_HEIGHT;
            int visibleCount = Math.min(MAX_VISIBLE_SUGGESTIONS,
                    suggestions.size() - suggestionScrollOffset);
            if (x >= popupX() && x <= popupX() + PANEL_WIDTH - 40
                    && row >= 0 && row < visibleCount
                    && y < popupY() + visibleCount * SUGGESTION_ROW_HEIGHT) {
                chooseSuggestion(suggestionScrollOffset + row);
                return true;
            }
        }
        return super.mouseClicked(logicalEvent, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double logicalX = ResponsiveUi.toLogicalX(mouseX, width, height, PANEL_WIDTH, PANEL_HEIGHT);
        double logicalY = ResponsiveUi.toLogicalY(mouseY, width, height, PANEL_WIDTH, PANEL_HEIGHT);
        if (suggestionsOpen() && scrollY != 0) {
            int visibleCount = Math.min(MAX_VISIBLE_SUGGESTIONS,
                    suggestions.size() - suggestionScrollOffset);
            boolean overPopup = logicalX >= popupX() && logicalX <= popupX() + PANEL_WIDTH - 40
                    && logicalY >= popupY() && logicalY < popupY() + visibleCount * SUGGESTION_ROW_HEIGHT;
            if (overPopup) {
                // Wheel navigation stops at either end; keyboard Tab navigation wraps.
                moveSuggestion(scrollY < 0 ? 1 : -1, false);
                return true;
            }
        }
        return super.mouseScrolled(logicalX, logicalY, scrollX, scrollY);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.fillGradient(0, 0, width, height, 0xFF202329, 0xFF101215);
        ResponsiveUi.push(graphics, width, height, PANEL_WIDTH, PANEL_HEIGHT);
        graphics.fill(left - 3, top - 3, left + PANEL_WIDTH + 3, top + PANEL_HEIGHT + 3, 0xAA050607);
        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xEE292D32);
        graphics.outline(left, top, PANEL_WIDTH, PANEL_HEIGHT, 0xFF555B62);
        graphics.fill(left + 1, top + 1, left + PANEL_WIDTH - 1, top + 36, 0xFF202428);
        graphics.fill(left + 18, top + 36, left + PANEL_WIDTH - 18, top + 37, 0xFF4C535B);
        graphics.pose().popMatrix();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        ResponsiveUi.push(graphics, width, height, PANEL_WIDTH, PANEL_HEIGHT);
        int logicalMouseX = ResponsiveUi.toLogicalX(mouseX, width, height, PANEL_WIDTH, PANEL_HEIGHT);
        int logicalMouseY = ResponsiveUi.toLogicalY(mouseY, width, height, PANEL_WIDTH, PANEL_HEIGHT);

        graphics.centeredText(font, "CONFIGURABLE MOVABLE BLOCK ENTITIES", left + PANEL_WIDTH / 2, top + 13, 0xFFFFFFFF);
        graphics.text(font, "BLOCK SEARCH", left + 20, top + 49, 0xFFBFC6CF);
        graphics.text(font, "Selected: " + selectedBlockId, left + 20, top + 91, 0xFF9FA8B2);
        graphics.text(font, "Movement", left + 22, top + 122, 0xFFE5E8EB);
        graphics.text(font, "Scheduled ticks", left + 22, top + 157, 0xFFE5E8EB);
        graphics.text(font, "Push cost (1 / n)", left + 22, top + 193, 0xFFE5E8EB);
        graphics.text(font, "Adhesion", left + 22, top + 227, 0xFFE5E8EB);
        graphics.text(font, "O opens this screen  •  Settings are saved locally.", left + 20, top + 268, 0xFF9CA4AC);
        graphics.text(font, "Tab / Shift+Tab navigate  •  Enter selects  •  ↑ / ↓ also navigate  •  Mouse wheel scrolls",
                left + 20, top + 286, 0xFF9CA4AC);

        super.extractRenderState(graphics, logicalMouseX, logicalMouseY, a);

        if (suggestionsOpen()) {
            int x = popupX();
            int y = popupY();
            int w = PANEL_WIDTH - 40;
            int visibleCount = Math.min(MAX_VISIBLE_SUGGESTIONS, suggestions.size() - suggestionScrollOffset);
            int h = visibleCount * SUGGESTION_ROW_HEIGHT;
            graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xF0101113);
            graphics.outline(x - 1, y - 1, w + 2, h + 2, 0xFF6B737C);
            for (int row = 0; row < visibleCount; row++) {
                int index = suggestionScrollOffset + row;
                BlockSuggestion suggestion = suggestions.get(index);
                int rowY = y + row * SUGGESTION_ROW_HEIGHT;
                if (index == suggestionIndex) {
                    graphics.fill(x, rowY, x + w, rowY + SUGGESTION_ROW_HEIGHT, 0xFF46515E);
                } else if ((row & 1) == 1) {
                    graphics.fill(x, rowY, x + w, rowY + SUGGESTION_ROW_HEIGHT, 0x661C2024);
                }
                graphics.text(font, suggestion.id(), x + 7, rowY + 5,
                        index == suggestionIndex ? 0xFFFFFF55 : 0xFFE6E9ED);
                int nameWidth = font.width(suggestion.name());
                graphics.text(font, suggestion.name(), x + w - nameWidth - 8, rowY + 5, 0xFF9FA8B2);
            }
        }
        graphics.pose().popMatrix();
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    private record BlockSuggestion(String id, String name, boolean prefixMatch) {}
}

