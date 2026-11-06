# HeyBroy0 – Configurable Movable Block Entities

## Project objective

Create a buildable Minecraft Fabric mod for **Minecraft 26.3** that allows configurable piston movement of block entities and multiblock structures.

The mod should extend vanilla piston behavior while preserving vanilla mechanics wherever possible.

The project already uses FrozenLib. Use it where appropriate, and avoid adding unnecessary dependencies.

The mod must build through GitHub Actions.

---

## Inspect and establish the development environment

Before implementing features:

1. Inspect the existing repository, project structure, Gradle configuration, and current source code.
2. Verify the exact Minecraft 26.3 version, Fabric Loader, Fabric API, FrozenLib, Java, Gradle, and mappings requirements.
3. Use the actual Minecraft 26.3 API names and properties. Do not rely on older Yarn names or outdated tutorials.
4. Verify that every dependency has a compatible 26.3 Fabric release.
5. Preserve existing project functionality and coding conventions.
6. Do not upgrade or replace dependencies without a demonstrated reason.

Do not assume that APIs, methods, or fields from earlier Minecraft versions remain available.

## Create the configuration interface

Create a sleek dark-grey GUI using the existing project conventions and FrozenLib where useful.

Requirements:
- Support Mod Menu integration.
- Add a configurable keyboard shortcut to open the GUI.
- Use a clean, consistent dark-grey visual style.
- Ensure buttons, search fields, dropdowns, and other interactive elements render above their background panels.
- Ensure mouse input and keyboard interaction target the correct GUI elements.
- Support scrolling where lists exceed the available screen space.

### Main page

The main page should contain:

**Block search**
- Search blocks by registry identifier and, where available, display name.
- Show matching suggestions in a dropdown.
- Support keyboard navigation through suggestions.
- Tab accepts/fills the selected suggestion.
- Enter selects the highlighted suggestion or opens an exact match.
- Avoid silently replacing the user's input with an incorrect suggestion.

After selecting a block, display its current configuration next to the relevant controls.

**Block configuration controls**

| Setting | Options |
|---|---|
| Movement | Vanilla, Movable, Breakable, Immovable |
| Scheduled ticks | Vanilla, Transfer, Duplicate |
| Push cost | Positive integer denominator |

Define the movement options as follows:

- **Vanilla:** Preserve the block's existing vanilla piston behavior.
- **Movable:** Allow the block to be pushed, including supported block entities, even when vanilla would normally prevent movement.
- **Breakable:** Destroy the block when a piston attempts to move it, following appropriate vanilla destruction and drop behavior.
- **Immovable:** Prevent piston movement and destruction of the block.

Do not let these settings accidentally bypass safety checks or cause block entity duplication.

### Changed blocks page

Add a separate page accessible through a navigation button beside the main content.

Show a scrollable list of blocks whose configurations differ from their effective defaults.

Each entry should have:

- **Revert:** Remove the custom configuration and restore its default behavior.
- **Focus:** Return to the main page and populate the search bar with that block.

Reverting should only affect the selected block.

---

## Define push cost

Push cost represents how much of the vanilla piston push capacity a block consumes.

The setting is represented by a positive integer denominator.

Examples:

| Denominator | Cost per block |
|---|---|
| 1 | 1 |
| 2 | 1/2 |
| 3 | 1/3 |
| 4 | 1/4 |

The default is 1.

Calculate total movement cost as:

`Total cost = sum of 1 / denominator for each moved block`

Use the vanilla 12-block capacity as the default total cost budget.

For example, two blocks with denominator 2 consume the same budget as one normal block.

Requirements:
- Accept positive integers only.
- Never permit zero or negative values.
- Calculate without rounding away fractional costs.
- Count each physical block only once.
- Include additional multiblock components in the cost calculation.
- Exclude destroyed blocks from movement cost.
- Ensure the movement resolver and its internal collections can safely handle configurations that allow more than 12 physical blocks.
- Preserve vanilla limits for blocks configured to use vanilla behavior.

Avoid implementing an unlimited movement system. Apply sensible safety limits if required by the actual Minecraft implementation.

---

## Scheduled-tick behavior

Scheduled-tick handling must be configurable per block.

Three options:

**VANILLA**
- Default.
- Do not interfere with vanilla scheduled-tick behavior.
- Preserve normal cancellation and execution semantics.

**TRANSFER**
- Move eligible pending block ticks from the original position to the destination.
- Preserve their original trigger time, remaining delay, priority, and ordering as closely as the vanilla scheduler permits.
- Do not restart the timer simply because a block moved.

**DUPLICATE**
- Leave the original scheduled tick untouched.
- Create an equivalent pending tick for the destination.
- Preserve its timing and priority.

Requirements:
- Perform tick operations exactly once per movement transaction.
- Avoid creating repeated duplicates during piston animation or landing.
- Use Minecraft's existing tick scheduler.
- Do not create a separate custom tick scheduler.
- Do not change fluid tick behavior by default.
- Do not alter scheduled ticks for blocks configured as Vanilla.

Transfer and Duplicate are explicit opt-in behaviors. Existing vanilla redstone behavior must remain unchanged under the default setting.

If the relevant scheduler operation requires internal access, investigate the 26.3 implementation and use the smallest appropriate Mixin or access widener.

---

## Implement piston push groups

Create a temporary movement context for every piston push or retraction operation.

A push group includes:
- Every block being moved by that piston operation.
- Relevant block entity data.
- Original and destination positions.
- Multiblock relationships.
- Piston-head creation or retraction information.

The context must remain available throughout the movement and landing process, including relevant sub-tick block events.

It must not be shared accidentally between unrelated piston operations.

### Group destination awareness

Implement group-scoped destination awareness for survival checks.

When a block in the group performs a survival or relevant neighbor-shape check, it should perceive:

- Itself at its intended destination.
- Other moving group members at their intended destinations.
- A moved block's original position as empty, unless another group member occupies it in the final arrangement.
- Unrelated blocks exactly as they exist in the real world.

This is NOT a request for a full projected-world implementation.

Only the participating push group should receive this temporary behavior.

### Initial movement checks

When the piston initially resolves its movement:
- Establish the complete movement group before performing dependent survival checks.
- Include relevant multiblock companions and supports.
- Allow blocks to pass survival checks when their required support is part of the same movement group.
- Do not let processing order determine whether a block survives.

For example, a torch should not break simply because its supporting block is scheduled to land after it.

### Landing checks

During landing:
- Blocks must see their group companions and supporting blocks in their intended or already completed positions.
- A block should not disappear because another group member has not physically landed yet.
- Once a block has landed, its actual world state should be used.
- Remove completed members from active group tracking when they are no longer needed.

Do not modify unrelated world queries or globally change normal survival behavior.

---

## Multiblock movement and coordinated landing

Support vanilla and modded structures containing multiple connected blocks.

Examples:
- Doors.
- Beds.
- Tall plants.
- Double chests.
- Other modded multiblocks.

### Structure resolution

Before movement:
1. Identify all components belonging to a movable structure.
2. Include all required companions in the movement group.
3. Calculate their destination positions.
4. Validate the complete structure.
5. Prevent partial movement when the structure cannot move as a whole.

Preserve individual block states and relevant block entity data.

Do not assume that a property called `type`, `part`, or `half` has a universal meaning. Use appropriate vanilla-specific handling or registered mod adapters.

Tiny Multiblock Lib may be investigated as a possible helper, but do not add it automatically. Verify its actual 26.3 Fabric compatibility and whether it provides the required functionality.

### Coordinated landing

When a multiblock component lands:

- Its required companions must land in the same coordinated landing operation.
- Perform the operation synchronously and prevent unrelated block updates from interrupting partial structure placement.
- Ensure the complete structure is placed before ordinary neighbor notifications where possible.
- Avoid duplicate placement, duplicated block entities, or multiple landing operations for the same component.

This requirement applies to the multiblock and its companions, not automatically to every block in the entire piston push group.

### Fast landing

Some blocks may land immediately rather than following the normal moving-block animation.

When this happens:
- Mark the block as landed.
- Remove it from active movement tracking once its actual destination state is established.
- If it belongs to a multiblock, immediately coordinate the companion's landing.
- Prevent the companion from being processed again by a later scheduled landing event.

Make landing operations idempotent so multiple update paths cannot cause duplication or corruption.

---

## Move block entities safely

Support moving blocks containing block entities and persistent data.

Examples include:
- Chests and inventories.
- Furnaces and processing machines.
- Modded machines.
- Other data-storing blocks.

Requirements:
- Preserve inventories.
- Preserve machine progress and relevant timers.
- Preserve persistent custom data.
- Restore data at the correct destination.
- Avoid duplicate or invalid block entities.
- Handle failed movement safely.
- Use the current Minecraft 26.3 serialization APIs.

Do not assume every block entity is movable. Provide a compatibility mechanism for mod developers to control or implement their block entity movement behavior.

---

## Implement configurable adhesion

Each block should have five adhesion modes.

**1. VANILLA**
- Default.
- Preserve existing vanilla adhesion and stickiness behavior.

**2. ADHESIVE**
- All six faces allow adhesion.

**3. GLAZED**
- No faces allow adhesion.

**4. HITBOX**
- Automatically determine adhesive faces using the block's selected shape.
- Default shape source: outline/selection shape.
- Default minimum contact square: 3×3 pixels.
- Default depth sampling range: 2 voxels inward from the selected face.

**5. ADVANCED**
- Selecting this option must not automatically open another screen.
- Display a separate Configure button beside the option.
- Clicking Configure opens the advanced adhesion editor.

Keep adhesion behavior independent of block movability, redstone functionality, and unrelated block-state changes.

Preserve vanilla compatibility rules such as slime/honey incompatibilities unless explicitly overridden by an intended feature.

---

## Analyze block properties and geometry

Create an automatic analysis system that discovers which block-state properties influence orientation and shape.

Use the actual Minecraft 26.3 BlockState and StateDefinition APIs.

For each registered block:

1. Discover its available block-state properties.
2. Inspect the possible values of each property.
3. Test property alternatives against representative states.
4. Identify properties that change orientation.
5. Identify properties that change the selected geometry.
6. Exclude properties that do not affect either.
7. Cache the resulting analysis.

Do not hardcode assumptions based solely on property names.

For example, `facing`, `axis`, `type`, `half`, `shape`, and `hinge` do not all mean the same thing across blocks.

### State-dependent geometry

Analyze relevant property combinations when multiple properties jointly affect geometry.

Examples:
- Fences changing connection arms.
- Walls changing connections.
- Panes changing connections.
- Stairs changing facing, half, and corner shape.
- Slabs changing top, bottom, and double geometry.
- Composters changing shape based on level.

It should not blindly enumerate every possible combination of every block property.

Use this approach:
- First identify properties that affect geometry.
- Then evaluate combinations of those relevant properties when practical.
- Use bounded analysis for unusually large state spaces.
- Provide an adapter or safe fallback when complete analysis is impractical.

A fence's four directional connection properties should be analyzed in their relevant combinations, rather than assuming the disconnected default state represents every fence shape.

### Orientation detection

Recognize valid orientation sources using property types and semantics, not just property names.

Support relevant Minecraft orientation information such as:
- Six-direction facing.
- Horizontal facing.
- Front-and-top orientation.
- Axis.
- Horizontal rotation.
- Custom modded orientation adapters.

A direction may provide a lead/front direction, but an axis alone does not establish a complete orientation.

It should not assign an arbitrary cardinal front to blocks without meaningful orientation.

For blocks without orientation:
- HITBOX should use the actual physical geometry of the six faces.
- ADHESIVE can enable all six faces.
- ADVANCED should allow manual orientation or custom configuration.

Changing the stickiness orientation must never change the block's actual Minecraft orientation or redstone behavior.

---

## Hitbox contact and depth analysis

This feature must determine whether the selected shape physically occupies a sufficiently large region near each block face.

Use a 16×16 reference grid for each of the six faces.

### Depth threshold

The default depth threshold is 2 voxels inward.

For each face:
- Inspect the shape within the first two voxel layers measured inward from that block boundary.
- Project occupied geometry onto the corresponding 16×16 face grid.
- Treat a pixel cell as occupied when the selected shape intersects its corresponding face-area column within the depth sampling range.
- Do not count zero-volume contact caused only by touching a boundary.

### Minimum contiguous square dimension

The default minimum is 3×3 pixels.

The threshold measures dimensions, not total area.

Use the largest completely occupied square within the projected grid.

Examples:

| Contact pattern | Result at 3×3 |
|---|---|
| 1×16 strip | Fail |
| 2×16 strip | Fail |
| 3×3 filled square | Pass |
| 4×4 filled square | Pass |
| 2-pixel perimeter rim | Fail |
| Full 16×16 face | Pass |

A composter is an important test case: its 2-pixel-wide top rim must not qualify as a 3×3 contact patch.

Use an efficient largest-filled-square algorithm.

Do not use total pixel count or the encompassing bounding box as the eligibility test.

### State-specific hitboxes

Store geometry measurements for the relevant block states.

For example:
- A fence without connections has different contact faces from a fence connected on one or more sides.
- A composter can have different geometry depending on its level.
- Stair shapes vary with state.

Do not calculate these measurements repeatedly during piston movement.

Cache threshold-independent results, such as the largest qualifying square dimension on each face.

Changing the minimum threshold must not require repeating the expensive geometry analysis.

---

## Separate analysis cache from player settings

Use two independent configuration files.

Example:

`config/<modid>/settings.json`

Contains:
- Movement preferences.
- Scheduled-tick preferences.
- Push-cost settings.
- Adhesion modes.
- Hitbox thresholds.
- Manual orientations.
- User overrides.

`config/<modid>/adhesion-analysis-cache.json`

Contains automatically generated information:
- Block identifiers.
- Property analysis.
- Orientation information.
- Relevant block-state combinations.
- Face-contact measurements.
- Analysis version and fingerprints.

The analysis cache must never overwrite player preferences.

### Automatic configuration import

When analysis produces defaults for a block that has no previous player configuration:

- Import the generated defaults into the player settings.
- Save the resulting configuration.
- Mark whether an entry is generated or manually modified.
- Never overwrite explicitly changed player settings.
- When analysis is refreshed, update generated defaults only where no player modification exists.

Preserve settings for modded blocks even when the corresponding mod is temporarily absent.

Use safe file-writing practices, such as writing to a temporary file before replacing the previous valid file.

---

## Analysis lifecycle and performance

Analysis should happen only when necessary.

Initial analysis must happen after all installed mods have completed block registration.

The analysis should be completed before entering a world, preferably incrementally at the title screen.

Use invalidation information including:
- Minecraft version.
- Mod IDs and versions.
- Block registry identifiers.
- Block-state property definitions and possible values.
- Analysis schema/version.

Only reanalyze affected blocks where practical.

If the Minecraft version or analysis algorithm changes incompatibly, invalidate the relevant results.

Do not:
- Enumerate block properties during piston movement.
- Recalculate shapes during piston movement.
- Perform full registry scans during normal gameplay.
- Perform unnecessary analysis on every startup.
- Modify player configuration during cache regeneration except for the controlled initial/default import described above.

Cache the results needed by the runtime adhesion resolver so ordinary adhesion checks are inexpensive.

Be careful with modded block methods that require a world, position, or collision context. Do not execute arbitrary world-dependent shape methods on background threads without establishing that they are safe.

For blocks whose geometry cannot be analyzed reliably from their block state, provide a custom adapter or a documented fallback.

---

## Advanced adhesion editor

The Advanced mode Configure button should open a dedicated editor.

Allow the player to:

- Select the geometry source.
- Configure the minimum contact square dimension.
- Configure the inward depth threshold.
- Inspect detected geometry-affecting properties.
- Inspect detected orientation-affecting properties.
- Select or override the lead-direction property.
- Manually configure the six adhesive faces.
- Override automatic analysis without changing actual block states.

Use a labeled cube net or rotatable cube diagram.

Use unambiguous face labels:
- F: Front.
- B: Back.
- L: Left.
- R: Right.
- U: Up.
- D: Down.

Green indicates enabled adhesion; red indicates disabled adhesion.

For blocks without a meaningful orientation, allow omnidirectional or manually controlled behavior instead of inventing a cardinal front.

Keep this editor understandable. Do not expose irrelevant technical block properties to ordinary players.

---

## ompatibility and mod developer support

Provide limited, useful extension points for other mods.

Allow developers to supply custom handling where automatic detection is insufficient, particularly for:
- Block entity movement.
- Multiblock relationships.
- Orientation resolution.
- Context-dependent geometry analysis.
- Special movement restrictions.

Prefer small interfaces or registration mechanisms over an unnecessarily complicated framework.

Do not assume that every modded block uses vanilla naming or property conventions.

Avoid hardcoding mod-specific behavior unless it is required for a known compatibility issue.

---

## Testing and regression protection

Test progressively instead of implementing everything simultaneously.

### Movement tests
- Normal movable block.
- Immovable block.
- Breakable block.
- Block entity with inventory.
- Furnace with progress.
- Multiple block entities in one push.

### Multiblock tests
- Doors.
- Beds.
- Tall plants.
- Double chests.
- Connected modded structures.
- Companion landing during sub-tick block events.
- Fast landing and companion handling.

### Survival tests
- Torch supported by another moving block.
- Attached blocks.
- Rails.
- Fences and walls.
- Blocks that depend on neighboring blocks.
- Both possible landing orders for support and supported blocks.

### Scheduled-tick tests
- Vanilla mode.
- Transfer mode.
- Duplicate mode.
- Repeater with pending scheduled state change.
- Observer during pulse generation.
- Redstone torch burnout.
- Piston-based redstone contraptions.

### Adhesion tests
- Full cubes.
- Slabs.
- Stairs.
- Fences.
- Walls.
- Glass panes.
- Composter.
- Multiple fence connection combinations.
- Blocks with state-dependent shapes.
- Blocks with no orientation property.

### Regression requirement

With default settings, existing vanilla piston and redstone behavior should remain unchanged except for the explicitly intended movement and survival fixes.

Do not claim compatibility solely because the project compiles.

---

##  GitHub Actions and build requirements

Make the repository buildable through GitHub Actions.

Create or update workflows to:

1. Run on pushes and pull requests.
2. Set up the Java version required by the verified Minecraft 26.3 build environment.
3. Set up Gradle using the repository's Gradle wrapper.
4. Cache Gradle dependencies.
5. Run the project build and available tests.
6. Produce the mod JAR.
7. Upload the resulting JAR as a workflow artifact.

Do not invent dependency versions, Maven coordinates, Java versions, or loader requirements.

Verify all of them against the actual project and available 26.3 Fabric releases.

Do not require release secrets for normal CI builds.

---

## Implementation order and final reporting

Implement incrementally in this order:

1. Repository inspection and verified build environment.
2. Basic GUI and persistent movement settings.
3. Movement resolver configuration.
4. Block entity movement.
5. Piston push groups and destination-aware survival checks.
6. Multiblock resolution and coordinated landing.
7. Scheduled-tick options.
8. Adhesion modes and cached geometry analysis.
9. Advanced editor and configuration migration.
10. Testing, compatibility fixes, and GitHub Actions.

Adjust the order only when repository architecture requires it.

After implementation, report:

- Features implemented.
- Important Minecraft 26.3 API or Mixin changes.
- Dependencies added or changed.
- Build and test results.
- Known limitations.
- GitHub Actions status.

Do not silently leave a feature partially implemented while describing it as complete.

**Overall design principle:**

Extend vanilla piston movement without unnecessarily replacing vanilla systems. Blocks in the same movement group should be able to validate their survival against their group's intended destination arrangement, while block entities, multiblock structures, scheduled ticks, and redstone behavior remain predictable and compatible.

1. Set up environment ready to build and create the requested menus as the template for future intended features. Then add the functionality related to making blocks configurable and block entities movable such as setting up config files and making the movable and tick transfer features working. Have menus and sliders ready for future progress.
2. Focus on finishing the other simple features in the menu and work on the piston push group block survival fixes and multiblock fast landing.
3. Focus on finishing adhesion settings
