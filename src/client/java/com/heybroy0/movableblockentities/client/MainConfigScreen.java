package com.heybroy0.movableblockentities.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

import static com.heybroy0.movableblockentities.client.ConfigStore.*;

/** Main page: all per-block settings stay visible on this screen. */
public final class MainConfigScreen extends Screen {
    private static final int PANEL_WIDTH = 520;
    private static final int PANEL_HEIGHT = 350;
    private static final int SUGGESTION_ROW_HEIGHT = 18;
    private static final int MAX_VISIBLE_SUGGESTIONS = 7;

    // Session-only search memory: a fresh game launch begins with an empty search.
    private static String lastSearchValue = "";
    private static String lastSelectedBlockId;
    private static String lastSelectedEntryId;

    private final Deque<String> searchUndoHistory = new ArrayDeque<>();
    private boolean applyingSearchUndo;
    private boolean suppressSuggestionActivation;
    private boolean suggestionsEnabled;
    private String lastObservedSearchValue = "";

    private String selectedBlockId;
    private String selectedEntryId;
    private final String focusContainerId;
    private final List<String> focusEntryIds;
    private int focusIndex;
    private BlockConfig config;
    private EditBox searchBox;
    private EditBox pushCostBox;
    private Button movementButton;
    private Button tickButton;
    private Button adhesionButton;
    private Button configureButton;
    private Button previousFocusButton;
    private Button nextFocusButton;
    private List<BlockSuggestion> suggestions = List.of();
    private int suggestionIndex;
    private int hoveredSuggestionIndex = -1;
    private int suggestionScrollOffset;
    private int left;
    private int top;
    private int searchY;

    public MainConfigScreen() {
        this(lastSelectedBlockId == null ? "minecraft:stone" : lastSelectedBlockId, lastSelectedEntryId);
    }

    public MainConfigScreen(String selectedBlockId) {
        this(selectedBlockId, null);
    }

    public MainConfigScreen(String selectedBlockId, String selectedEntryId) {
        this(selectedBlockId, selectedEntryId, null, List.of(), -1);
    }

    public MainConfigScreen(String selectedBlockId, String selectedEntryId,
                            String focusContainerId, List<String> focusEntryIds, int focusIndex) {
        super(Component.literal("Configurable Movable Block Entities"));
        this.focusContainerId = focusContainerId;
        this.focusEntryIds = focusEntryIds == null ? List.of() : List.copyOf(focusEntryIds);
        this.focusIndex = focusIndex;
        this.selectedBlockId = selectedBlockId == null || selectedBlockId.isBlank()
                ? (lastSelectedBlockId == null ? "minecraft:stone" : lastSelectedBlockId)
                : selectedBlockId;
        if (selectedBlockId != null && !selectedBlockId.isBlank()) {
            lastSelectedBlockId = selectedBlockId;
        }

        ConfigStore.TreeEntry selected = selectedEntryId == null || selectedEntryId.isBlank()
                ? ConfigStore.findTopmostBlockEntry(this.selectedBlockId)
                : ConfigStore.touchTreeEntry(selectedEntryId, this.selectedBlockId);
        if (selected != null) {
            this.selectedEntryId = selected.id();
        } else {
            // A not-yet-listed block gets a stable occurrence key. It is promoted into
            // the saved tree only when the player actually changes its settings.
            this.selectedEntryId = "block|" + this.selectedBlockId;
        }
        lastSelectedEntryId = this.selectedEntryId;
        this.config = ConfigStore.get(this.selectedBlockId, this.selectedEntryId);
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
        searchBox.setValue(lastSearchValue);
        lastObservedSearchValue = searchBox.getValue();
        searchBox.setResponder(this::onSearchValueChanged);
        refreshSuggestions(searchBox.getValue());
        addRenderableWidget(searchBox);

        if (focusEntryIds.size() > 1) {
            previousFocusButton = addRenderableWidget(Button.builder(Component.literal("‹"),
                            button -> moveFocusedEntry(-1))
                    .tooltip(Tooltip.create(Component.literal("Previous item in focused folder / selector")))
                    .bounds(left + PANEL_WIDTH - 70, top + 88, 22, 17).build());
            nextFocusButton = addRenderableWidget(Button.builder(Component.literal("›"),
                            button -> moveFocusedEntry(1))
                    .tooltip(Tooltip.create(Component.literal("Next item in focused folder / selector")))
                    .bounds(left + PANEL_WIDTH - 44, top + 88, 22, 17).build());
            refreshFocusButtons();
        }

        int labelX = left + 22;
        int controlX = left + 180;
        int controlWidth = 145;
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
                    applyFocusedSettings();
                }
            } catch (NumberFormatException ignored) {
                // Keep the last valid saved value while the user edits the field.
            }
        });
        addRenderableWidget(pushCostBox);

        adhesionButton = addButton("", controlX, row4, 112, 22, button -> cycleAdhesion());
        configureButton = addButton("Configure", controlX + 118, row4, 100, 22,
                button -> openAdhesionEditor());

        addButton("Changed blocks", left + 20, top + 307, 132, 22,
                button -> minecraft.gui.setScreen(new ChangedBlocksScreen(selectedBlockId, 0)));
        if (!focusEntryIds.isEmpty()) {
            addButton("Apply to focused", left + 160, top + 307, 142, 22,
                    button -> applyFocusedSettings());
        }
        addButton("Done", left + PANEL_WIDTH - 112, top + 307, 92, 22,
                button -> closeConfiguration());

        refreshControlLabels();
        // Keep the screen's focus manager and the text field in sync (also starts SDL text input).
        setInitialFocus(searchBox);
        setFocused(searchBox);
        suggestionsEnabled = false;
    }

    private Button addButton(String label, int x, int y, int w, int h, Button.OnPress action) {
        return addRenderableWidget(Button.builder(Component.literal(label), action).bounds(x, y, w, h).build());
    }

    private void moveFocusedEntry(int direction) {
        if (focusEntryIds.size() < 2) return;
        int nextIndex = focusIndex < 0 ? (direction > 0 ? 0 : focusEntryIds.size() - 1)
                : focusIndex + direction;
        if (nextIndex < 0 || nextIndex >= focusEntryIds.size()) return;
        String entryId = focusEntryIds.get(nextIndex);
        ConfigStore.TreeEntry entry = ConfigStore.findTreeEntry(entryId);
        String blockId = entry == null ? blockIdFromOccurrenceId(entryId) : entry.value();
        if (blockId == null || blockId.isBlank()) return;
        ConfigStore.TreeEntry touched = ConfigStore.touchTreeEntry(entryId, blockId);
        if (touched == null) return;
        focusIndex = nextIndex;
        selectedBlockId = blockId;
        selectedEntryId = touched.id();
        lastSelectedBlockId = blockId;
        lastSelectedEntryId = touched.id();
        config = ConfigStore.get(blockId, touched.id());
        suppressSuggestionActivation = true;
        try {
            searchBox.setValue(blockId);
        } finally {
            suppressSuggestionActivation = false;
        }
        searchBox.setCursorPosition(blockId.length());
        suggestionsEnabled = false;
        suggestions = List.of();
        pushCostBox.setValue(Integer.toString(config.pushCost));
        refreshControlLabels();
        refreshFocusButtons();
    }

    private String blockIdFromOccurrenceId(String id) {
        if (id == null) return null;
        if (id.startsWith("block|")) return id.substring("block|".length());
        if (id.startsWith("tagblock|")) {
            int separator = id.lastIndexOf('|');
            return separator < 0 ? null : id.substring(separator + 1);
        }
        return null;
    }

    private void refreshFocusButtons() {
        if (previousFocusButton == null || nextFocusButton == null) return;
        previousFocusButton.active = focusIndex > 0;
        nextFocusButton.active = focusIndex >= 0 && focusIndex < focusEntryIds.size() - 1;
    }

    private List<ConfigStore.TreeEntry> focusedSummaryEntries() {
        if (focusContainerId != null) {
            ConfigStore.TreeEntry container = ConfigStore.findTreeEntry(focusContainerId);
            return container == null ? List.of() : ConfigStore.childrenOf(container);
        }
        ConfigStore.TreeEntry selected = ConfigStore.findTreeEntry(selectedEntryId);
        if (selected != null && selected.parentId() != null) {
            ConfigStore.TreeEntry parent = ConfigStore.findTreeEntry(selected.parentId());
            if (parent != null) return ConfigStore.childrenOf(parent);
        }
        return ConfigStore.rootEntries();
    }

    private String summaryEntryName(ConfigStore.TreeEntry entry) {
        if (entry.type() == ConfigStore.TreeEntryType.FOLDER) return entry.name();
        if (entry.type() == ConfigStore.TreeEntryType.SELECTOR) {
            String name = entry.name();
            if (name.startsWith("#")) name = name.substring(1);
            int separator = name.indexOf(':');
            return separator >= 0 ? "#" + name.substring(separator + 1) : "#" + name;
        }
        String id = entry.value();
        int separator = id == null ? -1 : id.indexOf(':');
        String path = separator >= 0 ? id.substring(separator + 1) : (id == null ? "" : id);
        return path.replace('_', ' ');
    }

    private String focusedSummary() {
        List<ConfigStore.TreeEntry> entries = focusedSummaryEntries();
        StringBuilder summary = new StringBuilder("(").append(entries.size()).append(")");
        int count = Math.min(5, entries.size());
        for (int i = 0; i < count; i++) {
            summary.append(i == 0 ? " " : ", ").append(summaryEntryName(entries.get(i)));
        }
        return fitSingleLine(summary.toString(), 474 - (focusEntryIds.size() > 1 ? 52 : 0));
    }

    private String fitSingleLine(String value, int maxWidth) {
        String result = value == null ? "" : value;
        while (!result.isEmpty() && font.width(result) > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
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
        applyFocusedSettings();
        refreshControlLabels();
    }

    private void applyFocusedSettings() {
        if (!focusEntryIds.isEmpty()) {
            ConfigStore.applyConfigToEntries(config, focusEntryIds);
        }
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

    private void onSearchValueChanged(String value) {
        if (!suppressSuggestionActivation) {
            // The restored query stays quiet until the player edits it during this screen visit.
            suggestionsEnabled = true;
        }
        if (!applyingSearchUndo && !value.equals(lastObservedSearchValue)) {
            searchUndoHistory.addLast(lastObservedSearchValue);
            while (searchUndoHistory.size() > 64) {
                searchUndoHistory.removeFirst();
            }
        }
        lastObservedSearchValue = value;
        lastSearchValue = value;
        refreshSuggestions(value);
    }

    private void undoSearchValue() {
        if (searchUndoHistory.isEmpty() || searchBox == null) return;
        String previousValue = searchUndoHistory.removeLast();
        applyingSearchUndo = true;
        try {
            searchBox.setValue(previousValue);
            searchBox.setCursorPosition(previousValue.length());
        } finally {
            applyingSearchUndo = false;
        }
        lastObservedSearchValue = previousValue;
        lastSearchValue = previousValue;
    }

    private void refreshSuggestions(String value) {
        suggestionIndex = 0;
        String query = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            suggestions = List.of();
            suggestionScrollOffset = 0;
            hoveredSuggestionIndex = -1;
            suggestionIndex = 0;
            return;
        }

        List<BlockSuggestion> found = new ArrayList<>();
        if (value.startsWith("#")) {
            String tagQuery = value.substring(1).trim().toLowerCase(Locale.ROOT);
            for (String tagId : ConfigStore.blockTagIds(tagQuery)) {
                int members = ConfigStore.blockTagMembers(tagId).size();
                found.add(new BlockSuggestion("#" + tagId, "Block selector · " + members + " blocks", true, true));
            }
            found.sort(Comparator.comparing(BlockSuggestion::id));
            suggestions = List.copyOf(found);
            suggestionScrollOffset = 0;
            suggestionIndex = 0;
            hoveredSuggestionIndex = -1;
            return;
        }

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
                        id.toLowerCase(Locale.ROOT).startsWith(query) || path.startsWith(query), false));
            }
        }
        found.sort(Comparator.comparing(BlockSuggestion::prefixMatch).reversed()
                .thenComparing(BlockSuggestion::id));
        suggestions = List.copyOf(found);
        suggestionScrollOffset = 0;
        suggestionIndex = 0;
        hoveredSuggestionIndex = -1;
    }

    private boolean suggestionsOpen() {
        return suggestionsEnabled && searchBox != null && getFocused() == searchBox
                && searchBox.isFocused() && !searchBox.getValue().isBlank() && !suggestions.isEmpty();
    }

    private int popupX() { return left + 20; }
    private int popupY() { return searchY + 22; }

    private void moveSuggestion(int direction, boolean wrap) {
        if (suggestions.isEmpty()) return;

        hoveredSuggestionIndex = -1;
        if (wrap) {
            suggestionIndex = Math.floorMod(suggestionIndex + direction, suggestions.size());
        } else {
            suggestionIndex = Math.max(0, Math.min(suggestions.size() - 1, suggestionIndex + direction));
        }
        keepSuggestionSelectionVisible();
    }

    private void keepSuggestionSelectionVisible() {
        if (suggestionIndex < suggestionScrollOffset) {
            suggestionScrollOffset = suggestionIndex;
        } else if (suggestionIndex >= suggestionScrollOffset + MAX_VISIBLE_SUGGESTIONS) {
            suggestionScrollOffset = suggestionIndex - MAX_VISIBLE_SUGGESTIONS + 1;
        }
        int maxOffset = Math.max(0, suggestions.size() - MAX_VISIBLE_SUGGESTIONS);
        suggestionScrollOffset = Math.max(0, Math.min(maxOffset, suggestionScrollOffset));
    }

    private int visibleSuggestionCount() {
        return Math.min(MAX_VISIBLE_SUGGESTIONS,
                Math.max(0, suggestions.size() - suggestionScrollOffset));
    }

    private void scrollSuggestionWindow(int direction) {
        int maxOffset = Math.max(0, suggestions.size() - MAX_VISIBLE_SUGGESTIONS);
        suggestionScrollOffset = Math.max(0, Math.min(maxOffset, suggestionScrollOffset + direction));
        // Wheel scrolling only changes the viewport. The highlighted item is set by hover or keyboard.
    }

    private void updateSuggestionHover(double mouseX, double mouseY) {
        hoveredSuggestionIndex = -1;
        if (!suggestionsOpen()) return;

        int visibleCount = visibleSuggestionCount();
        boolean overPopup = mouseX >= popupX() && mouseX < popupX() + PANEL_WIDTH - 40
                && mouseY >= popupY() && mouseY < popupY() + visibleCount * SUGGESTION_ROW_HEIGHT;
        if (!overPopup) return;

        int row = (int) ((mouseY - popupY()) / SUGGESTION_ROW_HEIGHT);
        int index = suggestionScrollOffset + row;
        if (index >= suggestionScrollOffset && index < suggestionScrollOffset + visibleCount) {
            hoveredSuggestionIndex = index;
        }
    }

    private void chooseSuggestion(int index) {
        if (index < 0 || index >= suggestions.size()) return;
        BlockSuggestion suggestion = suggestions.get(index);
        if (suggestion.tag()) {
            ConfigStore.addBlockSelector(suggestion.id(), null);
            minecraft.gui.setScreen(new ChangedBlocksScreen(selectedBlockId, 0));
            return;
        }
        chooseBlock(suggestion.id());
    }

    private void chooseExactMatch() {
        String query = searchBox.getValue().trim();
        if (query.startsWith("#") && ConfigStore.isBlockTag(query.substring(1))) {
            ConfigStore.addBlockSelector(query.substring(1), null);
            minecraft.gui.setScreen(new ChangedBlocksScreen(selectedBlockId, 0));
            return;
        }
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
        lastSelectedBlockId = id;
        ConfigStore.TreeEntry topmost = ConfigStore.findTopmostBlockEntry(id);
        if (topmost != null) {
            ConfigStore.TreeEntry touched = ConfigStore.touchTreeEntry(topmost.id(), id);
            selectedEntryId = touched == null ? topmost.id() : touched.id();
            lastSelectedEntryId = selectedEntryId;
            config = ConfigStore.get(id, selectedEntryId);
        } else {
            selectedEntryId = "block|" + id;
            lastSelectedEntryId = selectedEntryId;
            config = ConfigStore.get(id, selectedEntryId);
        }
        // Updating the text to the selected block is not a user edit and must not reopen autocomplete.
        suppressSuggestionActivation = true;
        try {
            searchBox.setValue(id);
        } finally {
            suppressSuggestionActivation = false;
        }
        searchBox.setCursorPosition(id.length());
        suggestionsEnabled = false;
        suggestions = List.of();
        suggestionScrollOffset = 0;
        hoveredSuggestionIndex = -1;
        // Keep keyboard/SDL text input active so another query can be typed immediately.
        setFocused(searchBox);
        pushCostBox.setValue(Integer.toString(config.pushCost));
        refreshControlLabels();
    }

    private void openAdhesionEditor() {
        AdhesionEditorScreen.Page page = config.adhesion == Adhesion.HITBOX
                ? AdhesionEditorScreen.Page.HITBOX : AdhesionEditorScreen.Page.ADVANCED;
        minecraft.gui.setScreen(new AdhesionEditorScreen(selectedBlockId, selectedEntryId,
                page, focusEntryIds));
    }

    private void closeConfiguration() {
        ConfigStore.rebuildBehaviorCache();
        minecraft.gui.setScreen(null);
    }

    @Override
    public void onClose() {
        ConfigStore.rebuildBehaviorCache();
        super.onClose();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE) {
            closeConfiguration();
            return true;
        }
        if (searchBox != null && searchBox.isFocused()) {
            if (event.hasControlDown()) {
                if (event.key() == InputConstants.KEY_Z) {
                    undoSearchValue();
                    return true;
                }
                if (event.isSelectAll() || event.isCopy() || event.isCut() || event.isPaste()) {
                    // Delegate clipboard shortcuts to the focused EditBox.
                    if (searchBox.keyPressed(event)) return true;
                }
            }

            if (suggestionsOpen()) {
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
                    chooseSuggestion(hoveredSuggestionIndex >= 0
                            ? hoveredSuggestionIndex : suggestionIndex);
                    return true;
                }
            } else if (event.key() == InputConstants.KEY_RETURN) {
                chooseExactMatch();
                return true;
            }
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
            int visibleCount = visibleSuggestionCount();
            if (x >= popupX() && x <= popupX() + PANEL_WIDTH - 40
                    && y >= popupY() && row >= 0 && row < visibleCount) {
                chooseSuggestion(suggestionScrollOffset + row);
                return true;
            }
        }
        boolean insideSearch = searchBox != null
                && logicalEvent.x() >= searchBox.getX()
                && logicalEvent.x() < searchBox.getX() + searchBox.getWidth()
                && logicalEvent.y() >= searchBox.getY()
                && logicalEvent.y() < searchBox.getY() + searchBox.getHeight();

        if (!insideSearch) {
            // Clicking elsewhere dismisses autocomplete and relinquishes the text-input focus.
            suggestionsEnabled = false;
            hoveredSuggestionIndex = -1;
            if (getFocused() == searchBox) {
                setFocused(null);
            } else if (searchBox != null && searchBox.isFocused()) {
                searchBox.setFocused(false);
            }
        }

        boolean handled = super.mouseClicked(logicalEvent, doubleClick);
        if (insideSearch) {
            // Explicitly synchronize the Screen focus owner with the EditBox.
            setFocused(searchBox);
        }
        return handled || insideSearch;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        MouseButtonEvent logicalEvent = ResponsiveUi.toLogical(event, width, height, PANEL_WIDTH, PANEL_HEIGHT);
        float scale = ResponsiveUi.scale(width, height, PANEL_WIDTH, PANEL_HEIGHT);
        return super.mouseDragged(logicalEvent, dragX / scale, dragY / scale);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return super.mouseReleased(
                ResponsiveUi.toLogical(event, width, height, PANEL_WIDTH, PANEL_HEIGHT));
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        double logicalX = ResponsiveUi.toLogicalX(mouseX, width, height, PANEL_WIDTH, PANEL_HEIGHT);
        double logicalY = ResponsiveUi.toLogicalY(mouseY, width, height, PANEL_WIDTH, PANEL_HEIGHT);
        updateSuggestionHover(logicalX, logicalY);
        super.mouseMoved(logicalX, logicalY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double logicalX = ResponsiveUi.toLogicalX(mouseX, width, height, PANEL_WIDTH, PANEL_HEIGHT);
        double logicalY = ResponsiveUi.toLogicalY(mouseY, width, height, PANEL_WIDTH, PANEL_HEIGHT);
        if (suggestionsOpen() && scrollY != 0) {
            int visibleCount = visibleSuggestionCount();
            boolean overPopup = logicalX >= popupX() && logicalX < popupX() + PANEL_WIDTH - 40
                    && logicalY >= popupY()
                    && logicalY < popupY() + visibleCount * SUGGESTION_ROW_HEIGHT;
            if (overPopup) {
                scrollSuggestionWindow(scrollY < 0 ? 1 : -1);
                updateSuggestionHover(logicalX, logicalY);
                return true;
            }
        }
        return super.mouseScrolled(logicalX, logicalY, scrollX, scrollY);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        // Leave the area outside the menu panel transparent.
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
        graphics.text(font, focusedSummary(), left + 20, top + 91, 0xFF9FA8B2);
        graphics.text(font, "Movement", left + 22, top + 122, 0xFFE5E8EB);
        graphics.text(font, "Scheduled ticks", left + 22, top + 157, 0xFFE5E8EB);
        graphics.text(font, "Push cost (1 / n)", left + 22, top + 193, 0xFFE5E8EB);
        graphics.text(font, "Adhesion", left + 22, top + 227, 0xFFE5E8EB);
        graphics.text(font, MovableBlockEntitiesClient.getOpenConfigurationKeyName()
                + " opens this screen  •  Settings are saved locally.", left + 20, top + 268, 0xFF9CA4AC);
        graphics.text(font, "Tab / Shift+Tab navigate  •  Enter selects  •  Mouse wheel scrolls the list",
                left + 20, top + 286, 0xFF9CA4AC);

        super.extractRenderState(graphics, logicalMouseX, logicalMouseY, a);

        if (suggestionsOpen()) {
            int x = popupX();
            int y = popupY();
            int w = PANEL_WIDTH - 40;
            int visibleCount = visibleSuggestionCount();
            int h = visibleCount * SUGGESTION_ROW_HEIGHT;
            graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xF0101113);
            graphics.outline(x - 1, y - 1, w + 2, h + 2, 0xFF6B737C);
            for (int row = 0; row < visibleCount; row++) {
                int index = suggestionScrollOffset + row;
                BlockSuggestion suggestion = suggestions.get(index);
                int rowY = y + row * SUGGESTION_ROW_HEIGHT;
                if (index == hoveredSuggestionIndex) {
                    graphics.fill(x, rowY, x + w, rowY + SUGGESTION_ROW_HEIGHT, 0xFF596A7D);
                } else if (index == suggestionIndex) {
                    graphics.fill(x, rowY, x + w, rowY + SUGGESTION_ROW_HEIGHT, 0xFF46515E);
                } else if ((row & 1) == 1) {
                    graphics.fill(x, rowY, x + w, rowY + SUGGESTION_ROW_HEIGHT, 0x661C2024);
                }
                graphics.text(font, suggestion.id(), x + 7, rowY + 5,
                        (index == hoveredSuggestionIndex || index == suggestionIndex)
                                ? 0xFFFFFF55 : 0xFFE6E9ED);
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

    private record BlockSuggestion(String id, String name, boolean prefixMatch, boolean tag) {}
}

