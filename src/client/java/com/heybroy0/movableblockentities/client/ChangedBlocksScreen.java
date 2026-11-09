package com.heybroy0.movableblockentities.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hierarchical view of changed blocks, reusable block-tag selectors and user folders.
 * Root entries remain paged; expanded children are kept on the same page and scroll in-place.
 */
public final class ChangedBlocksScreen extends Screen {
    private static final int PANEL_WIDTH = 520;
    private static final int PANEL_HEIGHT = 350;
    private static final int PAGE_SIZE = 12;
    private static final int ROW_HEIGHT = 16;
    private static final int TOOLBAR_Y = 43;
    private static final int EDIT_Y = 68;
    private static final int TREE_X = 18;
    private static final int TREE_Y = 105;
    private static final int TREE_BOTTOM = 304;
    private static final int FOOTER_Y = 314;

    private final String returnBlock;
    private final Map<String, String> selectedItems = new LinkedHashMap<>();
    private int page;
    private int treeScrollOffset;
    private int selectionScrollOffset;
    private int left;
    private int top;
    private int hoveredRow = -1;
    private int rangeAnchor = -1;
    private int listWidth = 480;
    private boolean bulkMode;
    private String bulkParentId;
    private ConfigStore.TreeEntry pressedEntry;
    private double pressStartX;
    private double pressStartY;
    private boolean dragging;
    private int dragTargetIndex = -1;
    private boolean dragIntoFolder;
    private boolean dragAfterTarget;
    private String activeEntryId;
    private String entryDraft = "";

    private EditBox entryNameBox;
    private Button folderButton;
    private Button selectorButton;
    private Button renameButton;
    private Button bulkButton;
    private Button focusButton;
    private Button previousButton;
    private Button nextButton;
    private Button backButton;
    private List<VisibleRow> visibleRows = List.of();
    private List<ConfigStore.TreeEntry> roots = List.of();

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

        roots = ConfigStore.rootEntries();
        int pageCount = pageCount();
        page = Math.max(0, Math.min(page, pageCount - 1));
        rebuildVisibleRows();

        folderButton = addButton("New folder", left + 18, top + TOOLBAR_Y, 88, 20,
                button -> createFolder());
        selectorButton = addButton("Add selector", left + 110, top + TOOLBAR_Y, 92, 20,
                button -> addSelector());
        renameButton = addButton("Rename", left + 206, top + TOOLBAR_Y, 66, 20,
                button -> renameSelected());
        bulkButton = addButton(bulkMode ? "Done selecting" : "Bulk select",
                left + 276, top + TOOLBAR_Y, 104, 20, button -> {
                    if (!bulkMode) bulkParentId = activeFolderParent();
                    bulkMode = !bulkMode;
                    hoveredRow = -1;
                    rebuildVisibleRows();
                    refreshToolbar();
                });
        focusButton = addButton("Focus", left + 384, top + TOOLBAR_Y, 60, 20,
                button -> focusSelectedBlock());

        entryNameBox = new EditBox(font, left + 18, top + EDIT_Y, 360, 18,
                Component.literal("Folder name or block tag"));
        entryNameBox.setMaxLength(120);
        entryNameBox.setBordered(true);
        entryNameBox.setHint(Component.literal("Folder name or #namespace:tag"));
        entryNameBox.setValue(entryDraft);
        entryNameBox.setResponder(value -> entryDraft = value);
        addRenderableWidget(entryNameBox);

        previousButton = addButton("‹ Previous", left + 18, top + FOOTER_Y, 100, 20, button -> {
            page = Math.max(0, page - 1);
            treeScrollOffset = 0;
            rangeAnchor = -1;
            init();
        });
        nextButton = addButton("Next ›", left + 126, top + FOOTER_Y, 85, 20, button -> {
            page = Math.min(pageCount() - 1, page + 1);
            treeScrollOffset = 0;
            rangeAnchor = -1;
            init();
        });
        backButton = addButton("Back", left + PANEL_WIDTH - 126, top + FOOTER_Y, 108, 20,
                button -> minecraft.gui.setScreen(new MainConfigScreen(returnBlock)));

        refreshToolbar();
    }

    private Button addButton(String label, int x, int y, int width, int height, Button.OnPress action) {
        return addRenderableWidget(Button.builder(Component.literal(label), action)
                .bounds(x, y, width, height).build());
    }

    private int pageCount() {
        return Math.max(1, (roots.size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private void refreshToolbar() {
        if (renameButton == null) return;
        boolean canRename = ConfigStore.isUserTreeEntry(activeEntryId) && !bulkMode;
        ConfigStore.TreeEntry active = ConfigStore.findTreeEntry(activeEntryId);
        boolean canFocus = active != null && active.type() == ConfigStore.TreeEntryType.BLOCK && !bulkMode;
        renameButton.visible = canRename;
        renameButton.active = canRename;
        focusButton.visible = canFocus;
        focusButton.active = canFocus;
        bulkButton.setMessage(Component.literal(bulkMode ? "Done selecting" : "Bulk select"));
        previousButton.active = page > 0;
        nextButton.active = page < pageCount() - 1;
    }

    private String activeFolderParent() {
        ConfigStore.TreeEntry selected = ConfigStore.findTreeEntry(activeEntryId);
        if (selected == null) return null;
        return selected.type() == ConfigStore.TreeEntryType.FOLDER
                ? selected.id() : selected.parentId();
    }

    private void createFolder() {
        String name = entryDraft.trim();
        List<String> ids = new ArrayList<>(selectedItems.keySet());
        ConfigStore.createFolder(name, bulkMode ? bulkParentId : activeFolderParent(), ids);
        selectedItems.clear();
        activeEntryId = null;
        rangeAnchor = -1;
        entryDraft = "";
        bulkMode = false;
        bulkParentId = null;
        treeScrollOffset = 0;
        page = 0;
        init();
    }

    private void addSelector() {
        String raw = entryDraft.trim();
        if (raw.startsWith("#")) raw = raw.substring(1);
        if (!ConfigStore.isBlockTag(raw)) return;
        ConfigStore.addBlockSelector(raw, activeFolderParent());
        entryDraft = "";
        rangeAnchor = -1;
        page = 0;
        treeScrollOffset = 0;
        init();
    }

    private void focusSelectedBlock() {
        ConfigStore.TreeEntry active = ConfigStore.findTreeEntry(activeEntryId);
        if (active != null && active.type() == ConfigStore.TreeEntryType.BLOCK) {
            ConfigStore.TreeEntry touched = ConfigStore.touchTreeEntry(active.id(), active.value());
            minecraft.gui.setScreen(new MainConfigScreen(active.value(),
                    touched == null ? active.id() : touched.id()));
        }
    }

    private void renameSelected() {
        if (!ConfigStore.isUserTreeEntry(activeEntryId)) return;
        String name = entryDraft.trim();
        if (name.isBlank()) return;
        ConfigStore.renameTreeEntry(activeEntryId, name);
        entryDraft = "";
        init();
    }

    private void rebuildVisibleRows() {
        int start = page * PAGE_SIZE;
        int end = Math.min(roots.size(), start + PAGE_SIZE);
        List<VisibleRow> flattened = new ArrayList<>();
        for (int i = start; i < end; i++) {
            ConfigStore.TreeEntry root = roots.get(i);
            appendRows(flattened, root, 0, i == end - 1, List.of());
        }
        visibleRows = List.copyOf(flattened);
        int visibleCount = maxVisibleRows();
        treeScrollOffset = Math.max(0, Math.min(treeScrollOffset,
                Math.max(0, visibleRows.size() - visibleCount)));
        selectionScrollOffset = Math.max(0, Math.min(selectionScrollOffset,
                Math.max(0, selectedItems.size() - maxSelectionRows())));
    }

    private void appendRows(List<VisibleRow> rows, ConfigStore.TreeEntry entry,
                            int depth, boolean last, List<Boolean> ancestorLastFlags) {
        // Compatibility wrapper for a single root; the actual walk is iterative.
        Deque<PendingRow> pending = new ArrayDeque<>();
        pending.push(new PendingRow(entry, depth, last, ancestorLastFlags));
        while (!pending.isEmpty()) {
            PendingRow current = pending.pop();
            StringBuilder connector = new StringBuilder();
            for (boolean ancestorWasLast : current.ancestorLastFlags()) {
                connector.append(ancestorWasLast ? "   " : "|  ");
            }
            if (current.depth() > 0) connector.append(current.last() ? "`- " : "|- ");
            rows.add(new VisibleRow(current.entry(), current.depth(), connector.toString()));

            if (!current.entry().expanded() || current.entry().type() == ConfigStore.TreeEntryType.BLOCK) {
                continue;
            }
            List<ConfigStore.TreeEntry> children = ConfigStore.childrenOf(current.entry());
            for (int i = children.size() - 1; i >= 0; i--) {
                List<Boolean> childFlags = new ArrayList<>(current.ancestorLastFlags());
                childFlags.add(current.last());
                pending.push(new PendingRow(children.get(i), current.depth() + 1,
                        i == children.size() - 1, List.copyOf(childFlags)));
            }
        }
    }

    private int maxVisibleRows() {
        return Math.max(1, (TREE_BOTTOM - TREE_Y) / ROW_HEIGHT);
    }

    private int maxSelectionRows() {
        return Math.max(1, (TREE_BOTTOM - TREE_Y - 34) / ROW_HEIGHT);
    }

    private int visibleTreeRowAt(double mouseY) {
        int row = (int) ((mouseY - TREE_Y) / ROW_HEIGHT) + treeScrollOffset;
        return row >= treeScrollOffset && row < Math.min(visibleRows.size(),
                treeScrollOffset + maxVisibleRows()) ? row : -1;
    }

    private void selectRange(int targetIndex) {
        if (visibleRows.isEmpty() || targetIndex < 0 || targetIndex >= visibleRows.size()) return;
        if (rangeAnchor < 0 || rangeAnchor >= visibleRows.size()) {
            rangeAnchor = targetIndex;
        }
        int start = Math.min(rangeAnchor, targetIndex);
        int end = Math.max(rangeAnchor, targetIndex);
        for (int i = start; i <= end; i++) {
            ConfigStore.TreeEntry entry = visibleRows.get(i).entry();
            selectedItems.put(entry.id(), entry.name());
        }
    }

    private void toggleSelection(ConfigStore.TreeEntry entry) {
        if (selectedItems.containsKey(entry.id())) {
            selectedItems.remove(entry.id());
        } else {
            selectedItems.put(entry.id(), entry.name());
        }
    }

    private boolean hasChildren(ConfigStore.TreeEntry entry) {
        if (entry.type() == ConfigStore.TreeEntryType.BLOCK) return false;
        return !ConfigStore.childrenOf(entry).isEmpty();
    }

    private void handleTreeClick(MouseButtonEvent event, boolean doubleClick, double x, double y) {
        int rowIndex = visibleTreeRowAt(y);
        if (rowIndex < 0) return;
        VisibleRow row = visibleRows.get(rowIndex);
        ConfigStore.TreeEntry entry = row.entry();
        int rowY = TREE_Y + (rowIndex - treeScrollOffset) * ROW_HEIGHT;
        int disclosureX = TREE_X + 4 + row.depth() * 12;
        int rowEnd = TREE_X + listWidth;

        if (event.button() != 1) return;
        if (hasChildren(entry) && x >= disclosureX && x < disclosureX + 12) {
            activeEntryId = entry.id();
            ConfigStore.toggleExpanded(entry.id());
            if (entry.parentId() == null) page = 0;
            rangeAnchor = -1;
            refreshTree();
            refreshToolbar();
            return;
        }

        if (!bulkMode && entry.type() == ConfigStore.TreeEntryType.BLOCK
                && x >= rowEnd - 17) {
            ConfigStore.revertInstance(entry.value(), entry.id());
            rangeAnchor = -1;
            refreshTree();
            return;
        }

        if (bulkMode || event.hasShiftDown()) {
            if (event.hasShiftDown()) {
                if (!bulkMode) bulkParentId = entry.parentId();
                if (rangeAnchor < 0 || rangeAnchor >= visibleRows.size()) rangeAnchor = rowIndex;
                bulkMode = true;
                selectRange(rowIndex);
                refreshToolbar();
            } else {
                toggleSelection(entry);
                rangeAnchor = rowIndex;
            }
            // Bulk selection is not activation and must not reorder the tree.
            refreshTree();
            rangeAnchor = findVisibleRowIndex(entry.id());
            return;
        }

        // A passive click establishes focus but does not reorder the row underneath the pointer.
        // Reordering occurs only when the player activates it (opens or expands it).
        activeEntryId = entry.id();
        pressedEntry = entry;
        pressStartX = x;
        pressStartY = y;
        dragging = false;
        dragTargetIndex = -1;
        rangeAnchor = rowIndex;

        if (entry.type() == ConfigStore.TreeEntryType.BLOCK) {
            if (doubleClick) {
                ConfigStore.TreeEntry touched = ConfigStore.touchTreeEntry(entry.id(), entry.value());
                if (entry.parentId() == null) page = 0;
                minecraft.gui.setScreen(new MainConfigScreen(entry.value(),
                        touched == null ? entry.id() : touched.id()));
            }
        } else {
            entryDraft = entry.name();
            if (entryNameBox != null) entryNameBox.setValue(entryDraft);
            if (doubleClick) {
                ConfigStore.toggleExpanded(entry.id());
                if (entry.parentId() == null) page = 0;
                rangeAnchor = -1;
                refreshTree();
            }
            refreshToolbar();
        }
    }

    private int findVisibleRowIndex(String entryId) {
        for (int i = treeScrollOffset; i < Math.min(visibleRows.size(),
                treeScrollOffset + maxVisibleRows()); i++) {
            if (visibleRows.get(i).entry().id().equals(entryId)) return i;
        }
        return -1;
    }

    private void refreshTree() {
        roots = ConfigStore.rootEntries();
        page = Math.min(page, pageCount() - 1);
        rebuildVisibleRows();
    }

    private String entryLabel(ConfigStore.TreeEntry entry) {
        return switch (entry.type()) {
            case FOLDER -> "[Folder] " + entry.name();
            case SELECTOR -> entry.name();
            case BLOCK -> entry.value();
        };
    }

    private String fitText(String value, int maxWidth) {
        if (maxWidth <= 0) return "";
        String text = value;
        while (!text.isEmpty() && font.width(text) > maxWidth) {
            if (text.length() <= 4) return "";
            text = text.substring(0, text.length() - 4) + "...";
        }
        return text;
    }

    private void renderTree(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int treeRight = TREE_X + listWidth;
        graphics.fill(TREE_X - 2, TREE_Y - 2, treeRight, TREE_BOTTOM, 0x7720242A);
        int count = Math.min(maxVisibleRows(), Math.max(0, visibleRows.size() - treeScrollOffset));
        for (int visible = 0; visible < count; visible++) {
            int index = treeScrollOffset + visible;
            VisibleRow row = visibleRows.get(index);
            ConfigStore.TreeEntry entry = row.entry();
            int y = TREE_Y + visible * ROW_HEIGHT;
            boolean selected = selectedItems.containsKey(entry.id());
            boolean active = entry.id().equals(activeEntryId);
            if (dragging && dragTargetIndex == index && dragIntoFolder) {
                graphics.fill(TREE_X - 1, y, treeRight, y + ROW_HEIGHT, 0xFF456C55);
            } else if (index == hoveredRow) {
                graphics.fill(TREE_X - 1, y, treeRight, y + ROW_HEIGHT, 0xFF414D5B);
            } else if (selected || active) {
                graphics.fill(TREE_X - 1, y, treeRight, y + ROW_HEIGHT, 0xFF35414D);
            } else if ((visible & 1) == 1) {
                graphics.fill(TREE_X - 1, y, treeRight, y + ROW_HEIGHT, 0x44252B31);
            }

            int prefixX = TREE_X + 4 + row.depth() * 12;
            if (row.depth() > 0) {
                graphics.text(font, row.connector(), TREE_X + 2, y + 3, 0xFF8C98A5);
            }
            int iconX = TREE_X + 4 + row.depth() * 12;
            String disclosure = hasChildren(entry) ? (entry.expanded() ? "v" : ">") : " ";
            graphics.text(font, disclosure, iconX, y + 3, 0xFFB6C0CA);

            int textX = Math.max(prefixX + 12, TREE_X + 20 + row.depth() * 12);
            if (bulkMode) {
                graphics.text(font, selected ? "[x]" : "[ ]", textX, y + 3, selected ? 0xFF86D99B : 0xFFADB6C0);
                textX += 22;
            }
            int labelWidth = treeRight - textX - (entry.type() == ConfigStore.TreeEntryType.BLOCK && !bulkMode ? 20 : 5);
            graphics.text(font, fitText(entryLabel(entry), labelWidth), textX, y + 3,
                    entry.type() == ConfigStore.TreeEntryType.SELECTOR ? 0xFF8FC7E8 : 0xFFE4E8EC);
            if (entry.type() == ConfigStore.TreeEntryType.BLOCK && !bulkMode) {
                graphics.text(font, "x", treeRight - 13, y + 3, 0xFFDA8888);
            }
            if (dragging && dragTargetIndex == index && !dragIntoFolder) {
                int markerY = dragAfterTarget ? y + ROW_HEIGHT - 2 : y;
                graphics.fill(TREE_X - 1, markerY, treeRight, markerY + 2, 0xFF8FC7E8);
            }
        }
        graphics.text(font, "Drag to reorder · drop on a folder to move · drop in blank space to root",
                TREE_X, TREE_BOTTOM + 2, 0xFF87919B);
    }

    private void renderSelectionSidebar(GuiGraphicsExtractor graphics) {
        int x = TREE_X + listWidth + 8;
        graphics.fill(x, TREE_Y - 2, left + PANEL_WIDTH - 18, TREE_BOTTOM, 0xCC171B20);
        graphics.outline(x, TREE_Y - 2, left + PANEL_WIDTH - x - 18, TREE_BOTTOM - TREE_Y + 2, 0xFF4B555F);
        graphics.text(font, "SELECTED", x + 7, TREE_Y + 5, 0xFFE0E5EA);
        graphics.text(font, Integer.toString(selectedItems.size()), x + 7, TREE_Y + 19, 0xFF90D5A0);
        int visible = 0;
        for (Map.Entry<String, String> selected : selectedItems.entrySet()) {
            if (visible < selectionScrollOffset) {
                visible++;
                continue;
            }
            if (visible - selectionScrollOffset >= maxSelectionRows()) break;
            int y = TREE_Y + 36 + (visible - selectionScrollOffset) * ROW_HEIGHT;
            graphics.fill(x + 4, y, left + PANEL_WIDTH - 22, y + ROW_HEIGHT, 0x552E353D);
            graphics.text(font, fitText(selected.getValue(), left + PANEL_WIDTH - x - 38),
                    x + 8, y + 3, 0xFFE0E5EA);
            visible++;
        }
        graphics.text(font, "Click item to remove", x + 6, TREE_BOTTOM - 13, 0xFF84909B);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
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

        listWidth = bulkMode ? 324 : 480;
        graphics.centeredText(font, "CHANGED BLOCKS", left + PANEL_WIDTH / 2, top + 14, 0xFFFFFFFF);
        graphics.text(font, "Compact tree · folders and selectors stay on their page",
                left + 18, top + 31, 0xFF9FA8B2);
        graphics.text(font, "Root page " + (page + 1) + " / " + pageCount()
                + "  ·  " + ConfigStore.changedBlockIds().size() + " changed blocks",
                left + 18, top + 92, 0xFFB7C0C9);

        if (visibleRows.isEmpty()) {
            graphics.centeredText(font, "No changed blocks or saved folders yet.",
                    left + (bulkMode ? 180 : PANEL_WIDTH / 2), top + 190, 0xFF9FA8B2);
        }
        renderTree(graphics, logicalMouseX, logicalMouseY);
        if (bulkMode) renderSelectionSidebar(graphics);
        super.extractRenderState(graphics, logicalMouseX, logicalMouseY, a);
        graphics.pose().popMatrix();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        MouseButtonEvent logical = ResponsiveUi.toLogical(event, width, height, PANEL_WIDTH, PANEL_HEIGHT);
        double x = logical.x();
        double y = logical.y();

        if (x >= TREE_X - 2 && x < TREE_X + listWidth && y >= TREE_Y && y < TREE_BOTTOM) {
            handleTreeClick(logical, doubleClick, x, y);
            return true;
        }

        if (bulkMode) {
            int sideX = TREE_X + listWidth + 8;
            if (x >= sideX && x < PANEL_WIDTH - 18 && y >= TREE_Y + 36 && y < TREE_BOTTOM - 14) {
                int item = (int) ((y - (TREE_Y + 36)) / ROW_HEIGHT) + selectionScrollOffset;
                if (item >= 0 && item < selectedItems.size()) {
                    String id = new ArrayList<>(selectedItems.keySet()).get(item);
                    selectedItems.remove(id);
                }
                return true;
            }
        }

        return super.mouseClicked(logical, doubleClick);
    }


    private void updateDragTarget(double x, double y) {
        dragTargetIndex = -1;
        dragIntoFolder = false;
        dragAfterTarget = false;
        if (x < TREE_X - 2 || x >= TREE_X + listWidth || y < TREE_Y || y >= TREE_BOTTOM) return;

        int index = visibleTreeRowAt(y);
        if (index < 0 || index >= visibleRows.size()
                || pressedEntry == null || visibleRows.get(index).entry().id().equals(pressedEntry.id())) return;

        dragTargetIndex = index;
        VisibleRow target = visibleRows.get(index);
        int rowY = TREE_Y + (index - treeScrollOffset) * ROW_HEIGHT;
        double position = (y - rowY) / ROW_HEIGHT;
        dragAfterTarget = position >= 0.5;
        dragIntoFolder = target.entry().type() == ConfigStore.TreeEntryType.FOLDER
                && position >= 0.25 && position <= 0.75;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        MouseButtonEvent logical = ResponsiveUi.toLogical(event, width, height, PANEL_WIDTH, PANEL_HEIGHT);
        if (pressedEntry != null && event.button() == 1) {
            double dx = logical.x() - pressStartX;
            double dy = logical.y() - pressStartY;
            if (!dragging && dx * dx + dy * dy >= 16) dragging = true;
            if (dragging) {
                updateDragTarget(logical.x(), logical.y());
                return true;
            }
        }
        float scale = ResponsiveUi.scale(width, height, PANEL_WIDTH, PANEL_HEIGHT);
        return super.mouseDragged(logical, dragX / scale, dragY / scale);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        MouseButtonEvent logical = ResponsiveUi.toLogical(event, width, height, PANEL_WIDTH, PANEL_HEIGHT);
        if (dragging && pressedEntry != null) {
            updateDragTarget(logical.x(), logical.y());
            ConfigStore.TreeEntry target = dragTargetIndex >= 0
                    ? visibleRows.get(dragTargetIndex).entry() : null;
            ConfigStore.moveTreeEntry(pressedEntry, target, dragIntoFolder, dragAfterTarget);
            pressedEntry = null;
            dragging = false;
            dragTargetIndex = -1;
            dragIntoFolder = false;
            dragAfterTarget = false;
            refreshTree();
            return true;
        }
        pressedEntry = null;
        dragging = false;
        dragTargetIndex = -1;
        return super.mouseReleased(logical);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        double x = ResponsiveUi.toLogicalX(mouseX, width, height, PANEL_WIDTH, PANEL_HEIGHT);
        double y = ResponsiveUi.toLogicalY(mouseY, width, height, PANEL_WIDTH, PANEL_HEIGHT);
        if (x >= TREE_X && x < TREE_X + listWidth && y >= TREE_Y && y < TREE_BOTTOM) {
            hoveredRow = visibleTreeRowAt(y);
        } else {
            hoveredRow = -1;
        }
        super.mouseMoved(x, y);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double x = ResponsiveUi.toLogicalX(mouseX, width, height, PANEL_WIDTH, PANEL_HEIGHT);
        double y = ResponsiveUi.toLogicalY(mouseY, width, height, PANEL_WIDTH, PANEL_HEIGHT);
        if (scrollY == 0) return false;
        if (x >= TREE_X - 2 && x < TREE_X + listWidth && y >= TREE_Y && y < TREE_BOTTOM) {
            treeScrollOffset = Math.max(0, Math.min(
                    Math.max(0, visibleRows.size() - maxVisibleRows()),
                    treeScrollOffset + (scrollY < 0 ? 1 : -1)));
            return true;
        }
        if (bulkMode && x >= TREE_X + listWidth + 8 && x < PANEL_WIDTH - 18
                && y >= TREE_Y && y < TREE_BOTTOM) {
            selectionScrollOffset = Math.max(0, Math.min(
                    Math.max(0, selectedItems.size() - maxSelectionRows()),
                    selectionScrollOffset + (scrollY < 0 ? 1 : -1)));
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 41) { // SDL scancode for Escape
            minecraft.gui.setScreen(new MainConfigScreen(returnBlock));
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    private record PendingRow(ConfigStore.TreeEntry entry, int depth, boolean last,
                              List<Boolean> ancestorLastFlags) {}
    private record VisibleRow(ConfigStore.TreeEntry entry, int depth, String connector) {}
}
