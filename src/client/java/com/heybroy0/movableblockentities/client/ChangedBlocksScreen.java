package com.heybroy0.movableblockentities.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
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
    private final String returnEntryId;
    private final String returnFocusContainerId;
    private final List<String> returnFocusEntryIds;
    private final int returnFocusIndex;
    private final Map<String, ConfigStore.TreeEntry> selectedItems = new LinkedHashMap<>();
    private boolean singleSelectionAdded;
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
    private List<ConfigStore.TreeEntry> pressedEntries = List.of();
    private boolean pendingDeselect;
    private double pressStartX;
    private double pressStartY;
    private boolean dragging;
    private int dragTargetIndex = -1;
    private boolean dragIntoFolder;
    private boolean dragAfterTarget;
    private String activeEntryId;
    private ConfigStore.TreeEntry activeEntrySnapshot;
    private String entryDraft = "";

    private EditBox entryNameBox;
    private Button folderButton;
    private Button renameButton;
    private Button bulkButton;
    private Button focusButton;
    private Button deleteButton;
    private Button copyButton;
    private Button deselectAllButton;
    private Button undoButton;
    private Button redoButton;
    private Button configDirectoryButton;
    private Button previousButton;
    private Button nextButton;
    private Button backButton;
    private List<VisibleRow> visibleRows = List.of();
    private List<ConfigStore.TreeEntry> roots = List.of();

    public ChangedBlocksScreen(String returnBlock, int page) {
        this(returnBlock, null, page, null, List.of(), -1);
    }

    public ChangedBlocksScreen(String returnBlock, String returnEntryId, int page,
                               String focusContainerId, List<String> focusEntryIds, int focusIndex) {
        super(Component.literal("Changed Blocks"));
        this.returnBlock = returnBlock;
        this.returnEntryId = returnEntryId;
        this.returnFocusContainerId = focusContainerId;
        this.returnFocusEntryIds = focusEntryIds == null ? List.of() : List.copyOf(focusEntryIds);
        this.returnFocusIndex = focusIndex;
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

        folderButton = addButton("", left + 18, top + TOOLBAR_Y, 64, 24,
                "New folder", button -> createFolder());
        renameButton = addButton("", left + 86, top + TOOLBAR_Y, 36, 24,
                "Rename folder", button -> renameSelected());
        focusButton = addButton("", left + 126, top + TOOLBAR_Y, 36, 24,
                "Focus selected entry", button -> focusSelectedEntry());
        bulkButton = addButton("",
                left + 166, top + TOOLBAR_Y, 40, 24,
                bulkMode ? "Finish bulk selection" : "Bulk select",
                button -> {
                    if (!bulkMode) {
                        bulkMode = true;
                        ConfigStore.TreeEntry active = activeEntry();
                        bulkParentId = active == null ? null : active.parentId();
                        rangeAnchor = findVisibleRowIndex(activeEntryId);
                    } else {
                        bulkMode = false;
                        // Keep the complete group when leaving bulk mode. The next ordinary
                        // click can replace only its transient single-item addition.
                        bulkParentId = null;
                        selectionScrollOffset = 0;
                        singleSelectionAdded = false;
                    }
                    hoveredRow = -1;
                    rebuildVisibleRows();
                    if (bulkMode && selectedItems.isEmpty()) refreshToolbar();
                    else selectionChanged();
                    refreshToolbar();
                });
        deleteButton = addButton("", left + 210, top + TOOLBAR_Y, 30, 24,
                "Delete selected entries / revert blocks", button -> deleteSelectedEntries());
        undoButton = addButton("", left + 244, top + TOOLBAR_Y, 30, 24,
                "Undo last edit", button -> runUndo());
        redoButton = addButton("", left + 278, top + TOOLBAR_Y, 30, 24,
                "Redo last undone edit", button -> runRedo());
        copyButton = addButton("", left + 312, top + TOOLBAR_Y, 30, 24,
                "Copy selected entries", button -> copySelectedEntries());
        deselectAllButton = addButton("", left + PANEL_WIDTH - 96, top + TREE_Y + 1, 78, 16,
                "Deselect all selected entries", button -> deselectAll());
        deselectAllButton.visible = false;

        entryNameBox = new EditBox(font, left + 18, top + EDIT_Y, 360, 18,
                Component.literal("Search changed blocks"));
        entryNameBox.setMaxLength(160);
        entryNameBox.setBordered(true);
        entryNameBox.setHint(Component.literal("Search blocks, folders, and selectors"));
        entryNameBox.setValue(entryDraft);
        entryNameBox.setResponder(value -> {
            entryDraft = value;
            page = 0;
            treeScrollOffset = 0;
            refreshTree();
        });
        addRenderableWidget(entryNameBox);
        configDirectoryButton = addButton("", left + 386, top + EDIT_Y, 116, 18,
                "Open this mod's config folder", button -> ConfigStore.openConfigDirectory());

        previousButton = addButton("", left + 18, top + FOOTER_Y, 100, 20, button -> {
            page = Math.max(0, page - 1);
            treeScrollOffset = 0;
            rangeAnchor = -1;
            init();
        });
        nextButton = addButton("", left + 126, top + FOOTER_Y, 85, 20, button -> {
            page = Math.min(pageCount() - 1, page + 1);
            treeScrollOffset = 0;
            rangeAnchor = -1;
            init();
        });
        backButton = addButton("", left + PANEL_WIDTH - 126, top + FOOTER_Y, 108, 20,
                button -> {
                    boolean preserveFocus = !returnFocusEntryIds.isEmpty();
                    minecraft.gui.setScreen(new MainConfigScreen(returnBlock, returnEntryId,
                            null, preserveFocus ? returnFocusEntryIds : List.of(), -1));
                });

        refreshToolbar();
    }

    private Button addButton(String label, int x, int y, int width, int height, Button.OnPress action) {
        return addRenderableWidget(Button.builder(Component.literal(label), action)
                .bounds(x, y, width, height).build());
    }

    private Button addButton(String label, int x, int y, int width, int height,
                             String tooltip, Button.OnPress action) {
        return addRenderableWidget(Button.builder(Component.literal(label), action)
                .tooltip(Tooltip.create(Component.literal(tooltip)))
                .bounds(x, y, width, height).build());
    }

    private int pageCount() {
        if (hasSearchFilter()) return 1;
        return Math.max(1, (roots.size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private boolean hasSearchFilter() {
        return entryDraft != null && !entryDraft.isBlank();
    }

    private String normalizedSearchFilter() {
        return hasSearchFilter() ? entryDraft.trim().toLowerCase(java.util.Locale.ROOT) : "";
    }

    private void refreshToolbar() {
        if (renameButton == null) return;
        boolean canRename = ConfigStore.isUserTreeEntry(activeEntryId)
                && selectedItems.size() <= 1;
        ConfigStore.TreeEntry active = activeEntry();
        boolean canFocus = !selectedItems.isEmpty() || active != null;
        renameButton.visible = canRename;
        renameButton.active = canRename;
        focusButton.visible = canFocus;
        focusButton.active = canFocus;
        deleteButton.active = !selectedItems.isEmpty() || active != null;
        copyButton.active = !selectedItems.isEmpty() || active != null;
        deselectAllButton.visible = bulkMode;
        deselectAllButton.active = bulkMode && !selectedItems.isEmpty();
        undoButton.active = ConfigStore.canUndo();
        redoButton.active = ConfigStore.canRedo();
        bulkButton.setMessage(Component.empty());
        previousButton.active = page > 0;
        nextButton.active = page < pageCount() - 1;
    }

    private ConfigStore.TreeEntry activeEntry() {
        ConfigStore.TreeEntry stored = ConfigStore.findTreeEntry(activeEntryId);
        return stored != null ? stored : activeEntrySnapshot;
    }

    private String activeFolderParent() {
        ConfigStore.TreeEntry selected = activeEntry();
        if (selected == null) return null;
        return selected.type() == ConfigStore.TreeEntryType.FOLDER
                ? selected.id() : selected.parentId();
    }

    private void createFolder() {
        String name = entryDraft.trim();
        List<String> ids = new ArrayList<>(selectedItems.keySet());
        String destinationParent = bulkMode ? bulkParentId : activeFolderParent();
        ConfigStore.createFolder(name, destinationParent, ids);
        // Folder creation relocates the selected entries; preserve their selection identity.
        entryDraft = "";
        rangeAnchor = -1;
        if (bulkMode) bulkParentId = destinationParent;
        treeScrollOffset = 0;
        page = 0;
        refreshTree();
        refreshSelectedEntrySnapshots();
        selectionChanged();
        init();
    }

    private void focusSelectedEntry() {
        java.util.Set<String> selectedIds = new java.util.LinkedHashSet<>(selectedItems.keySet());
        if (selectedIds.isEmpty() && activeEntryId != null) selectedIds.add(activeEntryId);
        if (selectedIds.isEmpty()) return;

        List<String> focusTargets = ConfigStore.treeEntriesTopDown().stream()
                .filter(entry -> selectedIds.contains(entry.id()))
                .map(ConfigStore.TreeEntry::id)
                .toList();
        if (focusTargets.isEmpty()) return;

        ConfigStore.TreeEntry firstBlock = null;
        for (String targetId : focusTargets) {
            ConfigStore.TreeEntry target = ConfigStore.findTreeEntry(targetId);
            if (target == null) continue;
            if (target.type() == ConfigStore.TreeEntryType.BLOCK) {
                firstBlock = target;
            } else {
                List<ConfigStore.TreeEntry> contained = ConfigStore.blockEntriesUnder(targetId);
                if (!contained.isEmpty()) firstBlock = contained.get(0);
            }
            if (firstBlock != null) break;
        }
        if (firstBlock == null) return;

        // Store the exact selected entries in breadth-first tree order. Directories are
        // targets in their own right; settings applied to them include their descendants.
        minecraft.gui.setScreen(new MainConfigScreen(firstBlock.value(), firstBlock.id(),
                null, focusTargets, -1));
    }

    private MainConfigScreen mainScreenForBlock(ConfigStore.TreeEntry entry) {
        boolean insideFocusSnapshot = false;
        for (String targetId : returnFocusEntryIds) {
            ConfigStore.TreeEntry target = ConfigStore.findTreeEntry(targetId);
            if (target == null) {
                if (targetId.equals(entry.id())) insideFocusSnapshot = true;
            } else if (target.type() == ConfigStore.TreeEntryType.BLOCK) {
                insideFocusSnapshot |= target.id().equals(entry.id());
            } else {
                insideFocusSnapshot |= ConfigStore.blockEntriesUnder(targetId).stream()
                        .anyMatch(block -> block.id().equals(entry.id()));
            }
            if (insideFocusSnapshot) break;
        }
        // Preserve the snapshot only when the selected occurrence belongs to one of its
        // originally selected targets. An unrelated item starts with no focus.
        return new MainConfigScreen(entry.value(), entry.id(), null,
                insideFocusSnapshot ? returnFocusEntryIds : List.of(), -1);
    }

    private void deleteSelectedEntries() {
        List<String> ids = !selectedItems.isEmpty() ? new ArrayList<>(selectedItems.keySet())
                : activeEntryId == null ? List.of() : List.of(activeEntryId);
        if (ids.isEmpty()) return;
        ConfigStore.deleteTreeEntries(ids);
        selectedItems.clear();
        activeEntryId = null;
        activeEntrySnapshot = null;
        rangeAnchor = -1;
        bulkMode = false;
        bulkParentId = null;
        treeScrollOffset = 0;
        page = 0;
        refreshTree();
        refreshToolbar();
    }

    private void copySelectedEntries() {
        List<String> ids = !selectedItems.isEmpty() ? new ArrayList<>(selectedItems.keySet())
                : activeEntryId == null ? List.of() : List.of(activeEntryId);
        if (ids.isEmpty()) return;
        List<String> copies = ConfigStore.copyTreeEntries(ids);
        clearSelection();
        activeEntryId = copies.isEmpty() ? null : copies.get(copies.size() - 1);
        activeEntrySnapshot = activeEntryId == null ? null : ConfigStore.findTreeEntry(activeEntryId);
        addSelected(activeEntrySnapshot);
        rangeAnchor = -1;
        selectionChanged();
        refreshTree();
    }

    private void runUndo() {
        if (ConfigStore.undo()) {
            clearSelection();
            activeEntryId = null;
            activeEntrySnapshot = null;
            rangeAnchor = -1;
            selectionChanged();
            refreshTree();
        }
    }

    private void runRedo() {
        if (ConfigStore.redo()) {
            clearSelection();
            activeEntryId = null;
            activeEntrySnapshot = null;
            rangeAnchor = -1;
            selectionChanged();
            refreshTree();
        }
    }

    private void renameSelected() {
        if (!ConfigStore.isUserTreeEntry(activeEntryId)) return;
        String name = entryDraft.trim();
        if (name.isBlank()) return;
        ConfigStore.renameTreeEntry(activeEntryId, name);
        ConfigStore.TreeEntry renamed = ConfigStore.findTreeEntry(activeEntryId);
        if (renamed != null && selectedItems.containsKey(activeEntryId)) {
            selectedItems.put(activeEntryId, renamed);
        }
        activeEntrySnapshot = renamed;
        entryDraft = "";
        init();
    }

    private void rebuildVisibleRows() {
        int start = hasSearchFilter() ? 0 : page * PAGE_SIZE;
        int end = hasSearchFilter() ? roots.size() : Math.min(roots.size(), start + PAGE_SIZE);
        List<VisibleRow> flattened = new ArrayList<>();
        String query = normalizedSearchFilter();
        for (int i = start; i < end; i++) {
            ConfigStore.TreeEntry root = roots.get(i);
            if (query.isEmpty()) {
                appendRows(flattened, root, 0, i == end - 1, List.of());
            } else if (subtreeMatches(root, query)) {
                appendFilteredRows(flattened, root, i == end - 1, query);
            }
        }
        visibleRows = List.copyOf(flattened);
        int visibleCount = maxVisibleRows();
        treeScrollOffset = Math.max(0, Math.min(treeScrollOffset,
                Math.max(0, visibleRows.size() - visibleCount)));
        selectionScrollOffset = Math.max(0, Math.min(selectionScrollOffset,
                Math.max(0, selectedItems.size() - maxSelectionRows())));
    }

    private boolean matchesFilter(ConfigStore.TreeEntry entry, String query) {
        String searchable = (entry.name() + " " + entry.value() + " " + entry.type().name())
                .toLowerCase(java.util.Locale.ROOT);
        return searchable.contains(query);
    }

    private boolean subtreeMatches(ConfigStore.TreeEntry entry, String query) {
        Deque<ConfigStore.TreeEntry> pending = new ArrayDeque<>();
        pending.push(entry);
        while (!pending.isEmpty()) {
            ConfigStore.TreeEntry current = pending.pop();
            if (matchesFilter(current, query)) return true;
            for (ConfigStore.TreeEntry child : ConfigStore.childrenOf(current)) pending.push(child);
        }
        return false;
    }

    private void appendFilteredRows(List<VisibleRow> rows, ConfigStore.TreeEntry root,
                                   boolean last, String query) {
        Deque<FilteredPendingRow> pending = new ArrayDeque<>();
        pending.push(new FilteredPendingRow(root, 0, last, List.of(), false));
        while (!pending.isEmpty()) {
            FilteredPendingRow current = pending.pop();
            boolean matched = matchesFilter(current.entry(), query);
            List<ConfigStore.TreeEntry> children = current.entry().type() == ConfigStore.TreeEntryType.BLOCK
                    ? List.of() : ConfigStore.childrenOf(current.entry());
            boolean hasMatchingDescendant = false;
            if (!current.inheritedMatch()) {
                for (ConfigStore.TreeEntry child : children) {
                    if (subtreeMatches(child, query)) {
                        hasMatchingDescendant = true;
                        break;
                    }
                }
            }
            if (!current.inheritedMatch() && !matched && !hasMatchingDescendant) continue;

            StringBuilder connector = new StringBuilder();
            for (boolean ancestorWasLast : current.ancestorLastFlags()) {
                connector.append(ancestorWasLast ? "   " : "│  ");
            }
            if (current.depth() > 0) connector.append(current.last() ? "└─ " : "├─ ");
            rows.add(new VisibleRow(current.entry(), current.depth(), connector.toString()));

            boolean includeAllChildren = current.inheritedMatch() || matched;
            for (int i = children.size() - 1; i >= 0; i--) {
                ConfigStore.TreeEntry child = children.get(i);
                if (includeAllChildren || subtreeMatches(child, query)) {
                    List<Boolean> flags = new ArrayList<>(current.ancestorLastFlags());
                    flags.add(current.last());
                    pending.push(new FilteredPendingRow(child, current.depth() + 1,
                            i == children.size() - 1, List.copyOf(flags), includeAllChildren));
                }
            }
        }
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
                connector.append(ancestorWasLast ? "   " : "│  ");
            }
            if (current.depth() > 0) connector.append(current.last() ? "└─ " : "├─ ");
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

    private void selectRange(int targetIndex, boolean select) {
        if (visibleRows.isEmpty() || targetIndex < 0 || targetIndex >= visibleRows.size()) return;
        if (rangeAnchor < 0 || rangeAnchor >= visibleRows.size()) rangeAnchor = targetIndex;
        int start = Math.min(rangeAnchor, targetIndex);
        int end = Math.max(rangeAnchor, targetIndex);
        for (int i = start; i <= end; i++) {
            ConfigStore.TreeEntry entry = visibleRows.get(i).entry();
            if (select) addSelected(entry);
            else selectedItems.remove(entry.id());
        }
    }

    private void addSelected(ConfigStore.TreeEntry entry) {
        if (entry != null) selectedItems.put(entry.id(), entry);
    }

    private void clearSelection() {
        selectedItems.clear();
        singleSelectionAdded = false;
    }

    private void selectionChanged() {
        if (selectedItems.isEmpty()) {
            selectionScrollOffset = 0;
            activeEntryId = null;
            activeEntrySnapshot = null;
            singleSelectionAdded = false;
            if (!bulkMode) bulkParentId = null;
        } else {
            selectionScrollOffset = Math.max(0, Math.min(selectionScrollOffset,
                    Math.max(0, selectedItems.size() - maxSelectionRows())));
            if (activeEntryId == null || !selectedItems.containsKey(activeEntryId)) {
                singleSelectionAdded = false;
                ConfigStore.TreeEntry remaining = selectedItems.values().stream()
                        .reduce((first, second) -> second).orElse(null);
                activeEntrySnapshot = remaining;
                activeEntryId = remaining == null ? null : remaining.id();
            }
        }
        refreshToolbar();
    }

    private List<ConfigStore.TreeEntry> selectedEntriesForDrag() {
        List<ConfigStore.TreeEntry> ordered = new ArrayList<>();
        for (VisibleRow row : visibleRows) {
            ConfigStore.TreeEntry selected = selectedItems.get(row.entry().id());
            if (selected != null) ordered.add(selected);
        }
        for (ConfigStore.TreeEntry selected : selectedItems.values()) {
            if (ordered.stream().noneMatch(entry -> entry.id().equals(selected.id()))) ordered.add(selected);
        }
        return ordered;
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
        int disclosureX = TREE_X + 2 + row.depth() * 12;
        int rowEnd = TREE_X + listWidth;

        if (event.button() != 1) return;
        if (hasChildren(entry) && x >= disclosureX && x < disclosureX + 12) {
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
            refreshToolbar();
            return;
        }

        if (!bulkMode && (event.hasShiftDown() || event.hasControlDown())) {
            // Modifier-click is an immediate transition into an additive bulk selection.
            ConfigStore.TreeEntry previousActive = activeEntry();
            bulkMode = true;
            bulkParentId = previousActive == null ? entry.parentId() : previousActive.parentId();
            if (event.hasShiftDown()) {
                int anchor = findVisibleRowIndex(activeEntryId);
                if (anchor < 0) anchor = rowIndex;
                rangeAnchor = anchor;
                selectRange(rowIndex, true);
            } else {
                // Ctrl-click adds only this row; it does not infer a range.
                rangeAnchor = rowIndex;
                addSelected(entry);
            }
            activeEntryId = entry.id();
            activeEntrySnapshot = entry;
            singleSelectionAdded = false;
            pendingDeselect = false;
            pressedEntry = null;
            pressedEntries = List.of();
            dragging = false;
            refreshTree();
            selectionChanged();
            return;
        }

        if (bulkMode && event.hasShiftDown()) {
            boolean selectRangeState = !selectedItems.containsKey(entry.id());
            if (rangeAnchor < 0 || rangeAnchor >= visibleRows.size()) rangeAnchor = rowIndex;
            selectRange(rowIndex, selectRangeState);
            activeEntryId = entry.id();
            activeEntrySnapshot = entry;
            singleSelectionAdded = false;
            pendingDeselect = false;
            pressedEntry = null;
            pressedEntries = List.of();
            dragging = false;
            refreshTree();
            selectionChanged();
            return;
        }

        boolean wasSelected = selectedItems.containsKey(entry.id());
        if (doubleClick) {
            if (!bulkMode) clearSelection();
            addSelected(entry);
            singleSelectionAdded = false;
            pendingDeselect = false;
        } else if (wasSelected) {
            // Defer deselection until release so dragging a selected row can move the group.
            pendingDeselect = true;
        } else {
            if (!bulkMode) {
                // Replace only the previous ordinary single-click addition. Selections
                // retained from bulk mode remain in the group.
                if (singleSelectionAdded && activeEntryId != null) {
                    selectedItems.remove(activeEntryId);
                }
                singleSelectionAdded = true;
            } else if (selectedItems.isEmpty()) {
                // A bulk session started empty: place a new folder beside the first chosen item.
                bulkParentId = entry.parentId();
                singleSelectionAdded = false;
            }
            addSelected(entry);
            pendingDeselect = false;
        }

        activeEntryId = entry.id();
        activeEntrySnapshot = entry;
        rangeAnchor = rowIndex;
        selectionChanged();

        pressedEntry = doubleClick ? null : entry;
        pressedEntries = doubleClick ? List.of() : selectedEntriesForDrag();
        pressStartX = x;
        pressStartY = y;
        dragging = false;
        dragTargetIndex = -1;
        if (doubleClick) {
            pendingDeselect = false;
        }

        if (entry.type() == ConfigStore.TreeEntryType.BLOCK) {
            if (doubleClick) {
                ConfigStore.TreeEntry touched = ConfigStore.touchTreeEntry(entry.id(), entry.value());
                if (entry.parentId() == null) page = 0;
                minecraft.gui.setScreen(mainScreenForBlock(touched == null ? entry : touched));
            }
        } else {
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

    private void refreshSelectedEntrySnapshots() {
        Map<String, ConfigStore.TreeEntry> currentEntries = new LinkedHashMap<>();
        for (ConfigStore.TreeEntry entry : ConfigStore.treeEntriesTopDown()) {
            currentEntries.put(entry.id(), entry);
        }
        for (String id : new ArrayList<>(selectedItems.keySet())) {
            ConfigStore.TreeEntry current = currentEntries.get(id);
            if (current == null) selectedItems.remove(id);
            else selectedItems.put(id, current);
        }
        if (activeEntryId != null) {
            ConfigStore.TreeEntry current = currentEntries.get(activeEntryId);
            if (current != null) activeEntrySnapshot = current;
        }
    }

    private void deselectAll() {
        clearSelection();
        activeEntryId = null;
        activeEntrySnapshot = null;
        rangeAnchor = -1;
        selectionScrollOffset = 0;
        selectionChanged();
        refreshTree();
    }

    private String entryLabel(ConfigStore.TreeEntry entry) {
        return switch (entry.type()) {
            case FOLDER, SELECTOR -> entry.name();
            case BLOCK -> blockLabel(entry.value());
        };
    }

    private String blockLabel(String blockId) {
        if (blockId == null) return "";
        int separator = blockId.indexOf(':');
        String path = separator >= 0 ? blockId.substring(separator + 1) : blockId;
        StringBuilder result = new StringBuilder();
        for (String word : path.replace('_', ' ').split(" ")) {
            if (result.length() > 0) result.append(' ');
            if (!word.isEmpty()) result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private String configSummary(ConfigStore.TreeEntry entry) {
        if (entry.type() == ConfigStore.TreeEntryType.BLOCK) {
            ConfigStore.BlockConfig config = ConfigStore.get(entry.value(), entry.id());
            String movement = switch (config.movement) {
                case VANILLA -> "V";
                case MOVABLE -> "M";
                case BREAKABLE -> "B";
                case IMMOVABLE -> "I";
            };
            String ticks = switch (config.scheduledTicks) {
                case VANILLA -> "V";
                case TRANSFER -> "T";
                case DUPLICATE -> "D";
            };
            String adhesion = config.adhesion.name().toLowerCase(java.util.Locale.ROOT);
            adhesion = Character.toUpperCase(adhesion.charAt(0)) + adhesion.substring(1);
            String movementName = switch (config.movement) {
                case VANILLA -> "Vanilla";
                case MOVABLE -> "Movable";
                case BREAKABLE -> "Breakable";
                case IMMOVABLE -> "Immovable";
            };
            String ticksName = switch (config.scheduledTicks) {
                case VANILLA -> "Vanilla";
                case TRANSFER -> "Transfer";
                case DUPLICATE -> "Duplicate";
            };
            String pushCost = config.pushCost == 1 ? "1 for 1/1" : "1/n for n " + config.pushCost;
            return "| " + movementName + " | " + ticksName + " | " + pushCost + " | " + adhesion + " |";
        }
        int count = ConfigStore.blockEntriesUnder(entry.id()).size();
        return entry.type() == ConfigStore.TreeEntryType.FOLDER
                ? "Folder · " + count + " block entries"
                : "Selector · " + count + " matching blocks";
    }

    private String fitText(String value, int maxWidth) {
        if (maxWidth <= 0) return "";
        String text = value;
        while (!text.isEmpty() && ResponsiveUi.textWidth(font, text) > maxWidth) {
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

            if (row.depth() > 0) {
                ResponsiveUi.text(graphics, font, row.connector(), TREE_X + 2, y + 3, 0xFF8C98A5);
            }
            int iconX = TREE_X + 2 + row.depth() * 12;
            String disclosure = hasChildren(entry) ? (entry.expanded() ? "▾" : "▸") : " ";
            ResponsiveUi.text(graphics, font, disclosure, iconX, y + 3, 0xFFB6C0CA);

            int textX = iconX + 12;
            if (bulkMode) {
                ResponsiveUi.text(graphics, font, selected ? "[x]" : "[ ]", textX, y + 3,
                        selected ? 0xFF86D99B : 0xFFADB6C0);
                textX += 22;
            }
            int labelWidth = treeRight - textX - (entry.type() == ConfigStore.TreeEntryType.BLOCK && !bulkMode ? 20 : 5);
            int labelColor = switch (entry.type()) {
                case FOLDER -> 0xFFFFB45C;
                case SELECTOR -> 0xFF8FC7E8;
                case BLOCK -> 0xFFE4E8EC;
            };
            ResponsiveUi.text(graphics, font, fitText(entryLabel(entry), labelWidth), textX, y + 3, labelColor);
            if (entry.type() == ConfigStore.TreeEntryType.BLOCK && !bulkMode) {
                ResponsiveUi.text(graphics, font, "x", treeRight - 13, y + 3, 0xFFDA8888);
            }
            if (dragging && dragTargetIndex == index && !dragIntoFolder) {
                int markerY = dragAfterTarget ? y + ROW_HEIGHT - 2 : y;
                graphics.fill(TREE_X - 1, markerY, treeRight, markerY + 2, 0xFF8FC7E8);
            }
        }
        ResponsiveUi.text(graphics, font, "Drag to reorder · drop on a folder to move · selector members copy when moved out",
                TREE_X, TREE_BOTTOM + 2, 0xFF87919B);

        if (hoveredRow >= treeScrollOffset
                && hoveredRow < Math.min(visibleRows.size(), treeScrollOffset + maxVisibleRows())) {
            ConfigStore.TreeEntry hovered = visibleRows.get(hoveredRow).entry();
            String tooltip = configSummary(hovered);
            int tooltipWidth = ResponsiveUi.textWidth(font, tooltip) + 12;
            int tooltipX = Math.min(mouseX + 10, treeRight - tooltipWidth);
            int tooltipY = Math.min(mouseY + 16, TREE_BOTTOM - 19);
            graphics.fill(tooltipX, tooltipY, tooltipX + tooltipWidth, tooltipY + 19, 0xF0101113);
            graphics.outline(tooltipX, tooltipY, tooltipWidth, 17, 0xFF69737D);
            ResponsiveUi.text(graphics, font, tooltip, tooltipX + 6, tooltipY + 5, 0xFFE7ECF1);
        }
    }

    private void renderSelectionSidebar(GuiGraphicsExtractor graphics) {
        int x = TREE_X + listWidth + 8;
        graphics.fill(x, TREE_Y - 2, left + PANEL_WIDTH - 18, TREE_BOTTOM, 0xCC171B20);
        graphics.outline(x, TREE_Y - 2, left + PANEL_WIDTH - x - 18, TREE_BOTTOM - TREE_Y + 2, 0xFF4B555F);
        ResponsiveUi.text(graphics, font, "SELECTED", x + 7, TREE_Y + 5, 0xFFE0E5EA);
        ResponsiveUi.text(graphics, font, Integer.toString(selectedItems.size()), x + 7, TREE_Y + 19, 0xFF90D5A0);
        int visible = 0;
        for (Map.Entry<String, ConfigStore.TreeEntry> selected : selectedItems.entrySet()) {
            if (visible < selectionScrollOffset) {
                visible++;
                continue;
            }
            if (visible - selectionScrollOffset >= maxSelectionRows()) break;
            int y = TREE_Y + 36 + (visible - selectionScrollOffset) * ROW_HEIGHT;
            graphics.fill(x + 4, y, left + PANEL_WIDTH - 22, y + ROW_HEIGHT, 0x552E353D);
            ResponsiveUi.text(graphics, font, fitText(entryLabel(selected.getValue()), left + PANEL_WIDTH - x - 38),
                    x + 8, y + 3, 0xFFE0E5EA);
            visible++;
        }
        ResponsiveUi.text(graphics, font, "Click item to remove", x + 6, TREE_BOTTOM - 13, 0xFF84909B);
    }

    private void renderToolbarSymbols(GuiGraphicsExtractor graphics) {
        drawToolbarSymbol(graphics, folderButton, "📁");
        drawToolbarSymbol(graphics, renameButton, "✎");
        drawToolbarSymbol(graphics, focusButton, "◎");
        drawToolbarSymbol(graphics, bulkButton, bulkMode ? "☑" : "☷");
        drawToolbarSymbol(graphics, deleteButton, "❌");
        drawToolbarSymbol(graphics, undoButton, "↶");
        drawToolbarSymbol(graphics, redoButton, "↷");
        drawToolbarSymbol(graphics, copyButton, "⧉");
    }

    private void renderTextButtonLabels(GuiGraphicsExtractor graphics) {
        drawTextButtonLabel(graphics, configDirectoryButton, "📂 Config");
        drawTextButtonLabel(graphics, previousButton, "‹ Previous");
        drawTextButtonLabel(graphics, nextButton, "Next ›");
        drawTextButtonLabel(graphics, backButton, "Back");
        drawTextButtonLabel(graphics, deselectAllButton, "Deselect all");
    }

    private void drawTextButtonLabel(GuiGraphicsExtractor graphics, Button button, String label) {
        if (button == null || !button.visible || label == null || label.isEmpty()) return;
        ResponsiveUi.centeredText(graphics, font, label,
                button.getX() + button.getWidth() / 2,
                button.getY() + Math.max(2, (button.getHeight() - 8) / 2),
                button.active ? 0xFFFFFFFF : 0xFF777777);
    }

    private void drawToolbarSymbol(GuiGraphicsExtractor graphics, Button button, String symbol) {
        if (button == null || !button.visible) return;
        graphics.pose().pushMatrix();
        graphics.pose().translate(button.getX() + button.getWidth() / 2.0F,
                button.getY() + (button.getHeight() - 9) / 2.0F);
        float symbolScale = "☑".equals(symbol) ? 1.0F
                : ("↶".equals(symbol) || "↷".equals(symbol)) ? 1.9F : 1.55F;
        graphics.pose().scale(symbolScale, symbolScale);
        ResponsiveUi.centeredText(graphics, font, symbol, 0, 0, button.active ? 0xFFFFFFFF : 0xFF777777);
        graphics.pose().popMatrix();
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
        ResponsiveUi.centeredText(graphics, font, "CHANGED BLOCKS", left + PANEL_WIDTH / 2, top + 14, 0xFFFFFFFF);
        ResponsiveUi.text(graphics, font, "Compact tree · folders and selectors stay on their page",
                left + 18, top + 31, 0xFF9FA8B2);
        ResponsiveUi.text(graphics, font, (hasSearchFilter() ? "Filtered view" : "Root page " + (page + 1) + " / " + pageCount())
                + "  ·  " + ConfigStore.changedBlockIds().size() + " changed blocks",
                left + 18, top + 92, 0xFFB7C0C9);

        if (visibleRows.isEmpty()) {
            ResponsiveUi.centeredText(graphics, font, "No changed blocks or saved folders yet.",
                    left + (bulkMode ? 180 : PANEL_WIDTH / 2), top + 190, 0xFF9FA8B2);
        }
        renderTree(graphics, logicalMouseX, logicalMouseY);
        if (bulkMode) renderSelectionSidebar(graphics);
        super.extractRenderState(graphics, logicalMouseX, logicalMouseY, a);
        renderToolbarSymbols(graphics);
        renderTextButtonLabels(graphics);
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

        if (bulkMode && !selectedItems.isEmpty()) {
            int sideX = TREE_X + listWidth + 8;
            if (x >= sideX && x < PANEL_WIDTH - 18 && y >= TREE_Y + 36 && y < TREE_BOTTOM - 14) {
                int item = (int) ((y - (TREE_Y + 36)) / ROW_HEIGHT) + selectionScrollOffset;
                if (item >= 0 && item < selectedItems.size()) {
                    String id = new ArrayList<>(selectedItems.keySet()).get(item);
                    selectedItems.remove(id);
                    if (id.equals(activeEntryId)) {
                        activeEntrySnapshot = null;
                        activeEntryId = null;
                        singleSelectionAdded = false;
                    }
                    selectionChanged();
                    refreshTree();
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
        if (index < 0 || index >= visibleRows.size() || pressedEntry == null) return;
        if (pressedEntries.stream().anyMatch(source -> source.id().equals(visibleRows.get(index).entry().id()))) {
            // Dropping on any member of the moving selection is an invalid target,
            // not an instruction to move the group to the root.
            dragTargetIndex = -2;
            return;
        }

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
            if (!dragging && dx * dx + dy * dy >= 16) {
                dragging = true;
                pendingDeselect = false;
            }
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
            // A selected group is moved as one undoable tree operation.
            boolean moved = dragTargetIndex != -2
                    && ConfigStore.moveTreeEntries(pressedEntries, target, dragIntoFolder, dragAfterTarget);
            pressedEntry = null;
            pressedEntries = List.of();
            pendingDeselect = false;
            dragging = false;
            dragTargetIndex = -1;
            dragIntoFolder = false;
            dragAfterTarget = false;
            if (moved) {
                refreshTree();
                refreshSelectedEntrySnapshots();
                selectionChanged();
            }
            refreshTree();
            refreshToolbar();
            return true;
        }
        if (pendingDeselect && pressedEntry != null) {
            selectedItems.remove(pressedEntry.id());
            if (pressedEntry.id().equals(activeEntryId)) {
                activeEntrySnapshot = null;
                activeEntryId = null;
                singleSelectionAdded = false;
            }
            selectionChanged();
            refreshTree();
        }
        pressedEntry = null;
        pressedEntries = List.of();
        pendingDeselect = false;
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
        if (bulkMode && !selectedItems.isEmpty() && x >= TREE_X + listWidth + 8 && x < PANEL_WIDTH - 18
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
    private record FilteredPendingRow(ConfigStore.TreeEntry entry, int depth, boolean last,
                                      List<Boolean> ancestorLastFlags, boolean inheritedMatch) {}
    private record VisibleRow(ConfigStore.TreeEntry entry, int depth, String connector) {}
}
