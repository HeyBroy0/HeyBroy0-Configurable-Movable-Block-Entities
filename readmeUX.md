## Create the configuration interface - Already Done in Branch 'UX'
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
- Avoid replacing the user's input with an incorrect suggestion.
After selecting a block, display its current configuration next to the relevant controls.

**Block configuration controls**
| Main | Changed Blocks | 
|---|---|
| Movement | Vanilla, Movable, Breakable, Immovable |
| Scheduled ticks | Vanilla, Transfer, Duplicate |
| Push cost | Positive integer denominator |
| Adhesion | Vanilla, Adhesive, Glazed, Hitbox, Advanced | Hitbox, Advanced (Hidden unless Hitbox or Advanced) |

### Changed blocks page
Show a scrollable list of blocks whose configurations differ from their effective defaults.
Each entry should have:
- **Name and display of block**
- **Revert:** Remove the custom configuration and restore its default behavior.
- **Focus:** Return to the main page and populate the search bar with that block.

### Implement configurable adhesion
Each block should have five adhesion modes.
**1. VANILLA** Default. Preserve existing vanilla adhesion and stickiness behavior.
**2. ADHESIVE** All six faces allow adhesion.
**3. GLAZED** No faces allow adhesion.
**4. HITBOX**
- Automatically determine adhesive faces using the block's selected shape |Outline, visual, Hitbox|.
- Default shape source: outline/selection shape.
- Default minimum contact square: 3×3 pixels.
- Default depth sampling range: 2 voxels inward from the selected face.
**5. ADVANCED**
- Selecting this option must not automatically open another screen.
- Display a separate Configure button beside the option.
- Clicking Configure opens the advanced adhesion editor.

Keep adhesion behavior independent of block movability, redstone functionality, and unrelated block-state changes.
Preserve vanilla compatibility rules such as slime/honey incompatibilities unless explicitly overridden by an intended feature.

### Advanced/Hitbox adhesion editor
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

Use a labeled cube net or rotatable cube diagram, unambiguous face labels:
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
