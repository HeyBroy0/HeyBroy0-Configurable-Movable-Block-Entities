# Configurable Movable Block Entities — Selectors & Tree GUI prototype

A client-side Fabric GUI/configuration prototype for Minecraft 26.3. This branch extends the original GUI prototype with block-tag selectors, an expandable folder/selector tree, bulk organization, and independent settings for repeated block entries.

## In-game use

1. Use Minecraft 26.3 with Fabric Loader **0.20.1+**, Fabric API **0.165.2+26.3**, and Java **25**.
2. Build with `./gradlew build` or install the JAR produced by the branch's GitHub Actions workflow.
3. Launch Minecraft and press **O** to open the configuration screen. The key can be changed under **Options → Controls → Key Binds → Movable Block Entities**.

## Main configuration screen

- Configure the movement mode, scheduled-tick mode, push-cost denominator, and adhesion mode.
- Search blocks by display name or registry ID. Ordinary text searches all registered blocks regardless of namespace; prefix matches are ranked first.
- Enter a block-tag selector by starting the query with **#**. For example, `#minecraft:stairs`, `#c:dyed`, or `#minecraft:washed_away_by_fluids`. Tag suggestions are shown only when `#` is the first character.
- The suggestion list is limited to seven visible rows and can be navigated with the keyboard or mouse and scrolled independently. Tab / Shift+Tab move through suggestions; Enter selects the highlighted suggestion.
- Selecting a block opens its configuration. Search selects the highest-priority (topmost) occurrence of that block in the ordered tree when one exists.

## Adhesion editors

- **Hitbox** editor provides the face-grid geometry settings and manual face overrides.
- **Advanced** editor provides geometry source, minimum occupied-square size, depth, orientation, lead direction, and a clickable six-face cube net.
- Face states are shown with enabled/disabled color coding. Changes are saved as they are made.
- Back and Done return to the main screen.

## Changed Blocks: folders, selectors, and tree organization

- The Changed Blocks screen presents saved entries as a compact, selectable, hover-highlighted tree. Folder and selector children expand inline, with connector lines indicating nesting.
- Root entries remain paged. Expanded descendants stay with their parent page and the tree can scroll vertically, so large folders or selectors do not force every child onto another page.
- **New folder**, **Add selector**, **Rename**, and **Bulk select** are available in the toolbar. Only player-created folders/selectors can be renamed.
- Add a selector using a block tag ID (with or without the leading `#`). Expanding it reveals the blocks currently in that tag.
- Folders can contain blocks, selectors, and other folders. Select entries and create a folder to place the selection inside it.
- Bulk mode displays checkboxes and a persistent, scrollable selection sidebar. The selection remains while navigating root pages. Shift-click selects a range and automatically enters bulk mode.
- Rows can be reordered by dragging. Drop onto a folder to move an entry into it; drop in blank space to move it to the root. Selector-derived block rows are treated as separate occurrences when copied into a folder.
- Clicking a row selects it; double-clicking a block opens its settings, while double-clicking a folder or selector expands/collapses it. The disclosure control toggles expansion directly.
- Revert removes only the selected block occurrence's saved settings; it does not erase other copies of the same block.

## Duplicate blocks and priority

The tree is occurrence-based: the same registry block may appear more than once, including in different folders or under selectors. Each explicit occurrence has its own identity and saved configuration. Interacting with an entry promotes it within its own parent directory rather than moving it to the top of the entire tree. The first/topmost occurrence takes precedence when resolving a block through search. This allows a player to keep multiple independent configurations for the same block.

## Configuration storage

Settings are stored in `config/movable-block-entities/settings.properties`. The current tree-aware format stores entry type, parent, order, expansion state, and per-entry block settings, in addition to the general GUI options. The store writes through a temporary file and uses atomic replacement where supported. Back up the config before testing development builds that change its format.

## Build

Minecraft 26.3's current Fabric toolchain requires Java 25:

```sh
./gradlew clean build
```

The GitHub Actions workflow builds the project and uploads the JAR as an artifact.

## Scope and limitations

This branch is still a **GUI/configuration prototype**. It does not implement piston movement, a movement resolver, block-entity transfer, multiblock movement, scheduled-tick preservation, or runtime adhesion behavior. The GUI can save and organize settings, but those settings do not yet change in-world block behavior. A successful CI build verifies compilation and artifact packaging, not in-game interaction or gameplay behavior.
