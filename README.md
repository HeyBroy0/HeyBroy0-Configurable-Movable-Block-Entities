# Configurable Movable Block Entities — GUI Configuration Prototype

A client-side Fabric configuration prototype for Minecraft 26.3. The GUI provides block settings, searchable block-tag selectors, a hierarchical folder/selector tree, grouped selection and drag-and-drop organization, per-occurrence settings for duplicate blocks, and focus scopes for applying settings to selected groups.

> **Prototype scope:** This branch stores and organizes configuration data. It does not yet implement piston movement or apply the saved movement/adhesion options to in-world block behavior.

## In-game use

1. Use Minecraft 26.3 with Fabric Loader **0.20.1+**, Fabric API **0.165.2+26.3**, and Java **25**.
2. Build with `./gradlew build`, or install the JAR produced by the GitHub Actions build workflow.
3. Launch Minecraft and press **O** to open the configuration screen. Rebind the key under **Options → Controls → Key Binds → Movable Block Entities**.

## Main configuration screen

- Configure **Movement**, **Scheduled ticks**, **Push cost (1 / n)**, and **Adhesion** for the selected block occurrence.
- Search blocks by registry ID or display name. Text searches all registered blocks; prefix matches are ranked first.
- Enter a block-tag selector by starting the query with **#**. Examples include `#minecraft:stairs`, `#c:dyed`, and `#minecraft:washed_away_by_fluids`. Tag suggestions appear when `#` is the first character.
- Suggestions show up to seven rows at once. Use **Tab / Shift+Tab** or the **Up / Down** arrows to move through them, **Enter** to choose, the mouse to select, and the mouse wheel to scroll. **Ctrl+Z** undoes the search text.
- Choosing a block selects its highest-priority (topmost) occurrence when one exists and makes that occurrence the current focus target. Choosing a tag creates or reuses a selector and opens it in Changed Blocks as the focus target.
- The line below the search box displays the current focus scope, for example `Focus (2): stairs, slabs`. Search completion replaces the previous focus with the newly chosen block or selector.
- When a focus scope is active, changes to the current configuration are propagated to all block occurrences covered by that scope. **Apply to focused** explicitly applies the current values across the focus scope.

## Adhesion editors

The **Hitbox** and **Advanced** pages open from the Adhesion row when the selected adhesion mode supports an editor.

- **Hitbox** provides the face-selection grid and face-specific overrides.
- **Advanced** provides geometry source, minimum occupied-square size, depth, orientation, lead direction, and the six-face cube net.
- Face states use enabled/disabled colors. Changes are saved as they are made.
- **Back**, **Done**, or Escape returns to the main configuration screen with the current focus scope preserved.

## Changed Blocks tree

The Changed Blocks screen organizes saved block occurrences, folders, and reusable tag selectors in a hierarchical tree.

### Browsing and filtering

- Root entries are paged, with 12 root entries per page. Expanding a folder or selector displays its children inline on the same page; the tree scrolls vertically when needed.
- Use the search field to filter blocks, folders, and selectors. Matching descendants retain their ancestry in the filtered view. Filtering changes only what is displayed; it does not reorganize the saved tree.
- Folder names and selector names have distinct colors. Hover a row to inspect its configuration summary.
- The disclosure marker expands or collapses a folder or selector. Double-clicking a folder or selector also toggles expansion; double-clicking a block opens its configuration screen.

### Selection model

- An ordinary click selects one row. Clicking a selected row again removes it from the selection.
- When a group is retained after leaving Bulk Select, clicking another unselected row makes that row the active ordinary selection while preserving the rest of the group. The next unselected click replaces the previous ordinary single-row addition.
- **Shift-click** outside Bulk Select enters Bulk Select and adds the visible range from the previously active row to the clicked row.
- **Ctrl-click** outside Bulk Select enters Bulk Select and adds only the clicked row; it does not select a range.
- In Bulk Select, row checkboxes are shown. Shift-click adds or removes the range according to the clicked row's current selection state. Clicking an already selected row removes it.
- The selection sidebar lists selected entries. Click a sidebar entry to remove only that entry, or use **Deselect all** to clear the group while remaining in Bulk Select.
- Exiting Bulk Select preserves the selected group. The ordinary selection model remains available without displaying checkboxes.

### Organizing entries

The toolbar contains **New folder**, **Rename folder**, **Focus**, **Bulk Select**, **Delete / revert**, **Undo**, **Redo**, and **Copy**.

- **New folder with no selection:** creates a folder inside the active folder, or beside the active row when it is not a folder. With no active folder or row, it creates a root folder.
- **New folder with a selection:** always creates the new folder at the root and moves the selected entries into it. If both a directory and one of its descendants are selected, only the directory is moved; its descendants remain inside it.
- **Rename folder** is available for user-created folders. Selectors keep their tag identity and cannot be renamed.
- Drag selected rows to reorder them, move them above or below another row, or drop them onto a folder to nest them. Dropping in empty tree space moves the group to the root. Selected descendants are carried by a selected ancestor rather than detached from it.
- Moving an entry does not clear its selection. Dragging a selector-generated block member outside its selector creates an independent block occurrence; the live selector membership remains intact.
- **Copy** duplicates the selected entries in place, preserving folder structure and per-occurrence settings. Generated tag members remain supplied by the selector rather than being copied as explicit children.
- **Delete / revert** removes selected folders or selectors with their descendants, or reverts selected block occurrences. Reverting one occurrence does not remove other copies of the same block.
- **Undo** and **Redo** cover the latest 24 edits made during the current session.

## Focus scopes

Focus is a snapshot of the explicitly selected tree entries, not a keyboard cursor or a sequence of previous/next targets.

- Press **Focus** to capture the current selection. The snapshot is ordered top-down by tree level: root entries precede children, and children precede deeper descendants.
- A selected block targets that occurrence. A selected folder or selector remains a single focus target, while settings applied to that target cover its contained block occurrences.
- The main screen displays the selected focus targets in its focus summary. There are no previous/next focus arrow controls.
- While browsing Changed Blocks from a focus scope, returning to a block inside the original scope preserves that scope. Choosing an unrelated block starts a new focus target.

## Duplicate blocks and ordering

The tree is occurrence-based. The same registry block can appear multiple times, including in different folders or under selectors, and each explicit occurrence has its own identity and saved settings.

When a block is searched from the main screen, the first/topmost occurrence in the ordered tree is selected. Interacting with an occurrence promotes it within its own parent directory; it does not move the entry to the root or reorder unrelated directories. Selector-generated members are resolved from the live block tag and can be copied into folders as independent occurrences.

## Configuration storage and recovery

The GUI's editable source-of-truth is:

`config/movable-block-entities/settings.properties`

It stores the full tree (entry type, parent, order, expansion state) and per-occurrence block settings. The **Config** button on Changed Blocks opens the containing directory.

The GUI also generates a separate, flattened runtime snapshot:

`config/movable-block-entities/cache/runtime-config.properties`

This cache contains the effective settings for each block ID after the saved tree's priority rules and live tag-selector membership have been resolved. It is deliberately independent of the GUI tree format: runtime code should not need to understand folders, selectors, selection state, or screen classes. The cache has its own versioned schema and is replaced atomically. It is regenerated when settings are saved or loaded, and again after the active world supplies its block-tag data.

The standalone Java module under `core/` owns the runtime cache contract. `RuntimeConfigCache` can load the cache from a `Path`, query a block's immutable `BlockSettings`, or return the complete snapshot. It has no Minecraft, Fabric, or GUI dependencies. Future movement, adhesion, and block-entity systems should depend on this core API rather than importing GUI classes or parsing `settings.properties` themselves.

On load, the GUI store checks the saved hierarchy for entries that cannot be reached from the root. Entries with missing parents, invalid block parents, or parent-reference cycles are reconnected to the root, keeping their descendants intact. The repaired tree is saved automatically. This recovery does not require deleting the configuration file.

Back up both `settings.properties` and the generated cache before testing development builds that change persistence or cache formats. The editable settings file is authoritative; the runtime cache can be regenerated from it.

## Build

Minecraft 26.3's current Fabric toolchain requires Java 25:

```sh
# Build the complete Fabric GUI prototype and its bundled core API.
./gradlew clean build

# Build the GUI-independent runtime configuration API by itself.
./gradlew :core:build
```

The `:core` project is a pure Java library module and can be compiled without compiling the GUI source. The full Fabric artifact bundles that API for convenience. The core jar is also published as a separate build artifact, so a future non-GUI mod/runtime project can consume it independently.

## Scope and limitations

This branch is a **GUI/configuration prototype**. It does not implement piston movement, a movement resolver, block-entity transfer, multiblock movement, scheduled-tick preservation, or runtime adhesion behavior. The GUI stores and organizes these options, but they do not yet change in-world block behavior. A successful CI build verifies compilation and artifact packaging, not in-game interaction or gameplay behavior.
