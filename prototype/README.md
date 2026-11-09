# GUI prototype

A self-contained, navigable browser prototype for the Movable Block Entities configuration interface.

## Open it

Open `index.html` in a modern desktop or mobile browser. No build tool, server, package install, Minecraft instance, or mod dependency is required.

## Included flows

- **Configuration screen:** block search, movement mode, scheduled-tick mode, push-cost denominator, and all five adhesion modes are visible together.
- **Block autocomplete:** searches the sample block registry by display name or namespaced ID. Use the arrow keys to move the highlight, Tab to fill the input, Enter to select, and Escape to close the suggestion panel. Typing alone never changes the selected block.
- **Hitbox editor:** the adjacent Configure action is revealed when Hitbox is selected. Its editor exposes geometry source, minimum contact square, and inward depth.
- **Advanced editor:** the adjacent Configure action is revealed when Advanced is selected. Its editor exposes analysis overrides, lead direction, example detected properties, and a six-face cube net with togglable F/B/L/R/U/D faces.
- **Changed blocks:** review custom settings, focus a block back in the main screen, or revert it.
- **Local persistence:** prototype settings are stored in the browser's local storage.

## Scope and limitations

This is a GUI interaction prototype only. It does not load Minecraft registries, inspect real block states or hitboxes, write the mod's configuration files, or implement piston movement, block entities, scheduled ticks, multiblocks, adhesion resolution, or runtime integration. The autocomplete suggestions come from a small illustrative registry in `app.js`. The hitbox grid and detected property labels are sample visual data, not Minecraft analysis results.

The interface deliberately keeps all routine configuration controls on the main page. Only Hitbox and Advanced expose their corresponding secondary editor buttons.
