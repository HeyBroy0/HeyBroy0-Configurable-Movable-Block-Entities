Do not implement adhesion analysis yet. (Ignore until continue) Hitbox and Advanced adhesion settings won't work and that is fine.

### Analyze block properties and geometry
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
For example, 'facing', 'axis', 'type', 'half', 'shape', and 'hinge' do not all mean the same thing across blocks.

### State-dependent geometry
Analyze relevant property combinations when multiple properties jointly affect geometry.

Examples:
- Fences, Walls, Panes changing connection arms.
- Stairs have facing, half, and corner shape.
- Slabs have top, bottom, and double geometry.
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

'config/<modid>/settings.json'

Contains:
- Movement preferences.
- Scheduled-tick preferences.
- Push-cost settings.
- Adhesion modes.
- Hitbox thresholds.
- Manual orientations.
- User overrides.

'config/<modid>/adhesion-analysis-cache.json'

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

## Analysis Lifecycle and Performance
Block analysis should be performed outside normal gameplay and its results cached for runtime use.

### When analysis runs
- Run the initial analysis only after all installed mods have registered their blocks.
- Complete the analysis before the player enters a world. Preferably, perform it incrementally at the title screen.
- Reanalyze only when necessary. Avoid repeating the full analysis on every startup.

### When cached results become invalid
Each cached result must record enough information to determine whether it is still valid:
- Minecraft version.
- Mod IDs and versions.
- Block registry identifiers.
- Block-state properties and their possible values.
- Analysis schema or algorithm version.

Reanalyze only blocks affected by changes where practical. Invalidate results when the Minecraft version or analysis algorithm changes in a way that makes existing results incompatible.

### What analysis should provide
Cache the information required by the runtime adhesion resolver so that ordinary adhesion checks are inexpensive.

### Runtime and thread-safety requirements
Do not perform expensive analysis during piston movement or ordinary gameplay. In particular:
- Do not enumerate block-state properties during piston movement.
- Do not recalculate block shapes during piston movement.
- Do not scan the entire block registry during normal gameplay.

Be cautious with modded block methods that depend on a world, position, or collision context. Do not call potentially world-dependent shape methods on background threads unless their thread-safety is known.

### Unsupported or context-dependent geometry
Some blocks cannot be analyzed reliably using block-state information alone. Support these blocks through custom adapters or provide a documented fallback behavior.

### Configuration safety
Cache generation must not modify player configuration. The only exception is a controlled initial/default import, which should happen as a separate, explicitly defined operation.

---
