# Implementation status

This branch establishes the first Minecraft 26.3 Fabric implementation layer.

## Foundation and core settings

- Fabric Loom / Java 25 / Gradle 9.8.2 project scaffold and Gradle wrapper.
- GitHub Actions build workflow with JAR artifact upload.
- Per-block settings persisted atomically in `config/cmbe/settings.json`.
- Per-block Vanilla / Movable / Breakable / Immovable piston reactions.
- Movable block entity payload stored on the moving piston entity and restored on normal or fast landing.
- Scheduled-tick Vanilla / Transfer / Duplicate modes use Minecraft's native scheduled block-tick collection.
- Exact rational push-cost accounting with a vanilla budget of 12 and a 128-block hard ceiling.
- Adhesion Vanilla / All / None modes; Hitbox and Advanced retain vanilla behavior for now.
- Dark configuration UI with Block, Changed, Adhesion, Hitbox, and Advanced pages.
- Configurable key mapping. Hitbox/Advanced sliders are intentionally display-only.

## Still requiring implementation and regression testing

- Complete push-group destination-aware survival queries (especially torch/support ordering).
- Vanilla and modded multiblock discovery and atomic coordinated landing of companions.
- State-aware geometry/orientation analysis, hitbox-contact cache, and functional Advanced face editor.
- Mod Menu integration and full compatibility testing.
- Game-level regression tests for moving chests, machines, redstone tick modes, and piston contraptions.

Default settings remain vanilla. Only explicit per-block overrides should change behavior.
