# Configurable Movable Block Entities — GUI prototype

A client-only Fabric 26.3 prototype based on the GUI requirements in the parent repository. It intentionally contains no piston mixins, movement resolver, block-entity transfer, multiblock handling, scheduled-tick handling, or runtime adhesion implementation.

## In-game use

1. Install Fabric Loader **0.20.1+** for Minecraft **26.3**.
2. Install Fabric API **0.165.2+26.3**.
3. Put `movable-block-entities-gui-0.1.0.jar` in the instance `mods` folder.
4. Start Minecraft and press **O** (changeable in Options → Controls → Key Binds, under “Movable Block Entities”).
5. Search for a block by display name or registry ID. Use ↑/↓ to move through suggestions, Tab to fill the highlighted ID, and Enter to select it.
6. All basic options are on the main page. Selecting Hitbox or Advanced adhesion reveals its corresponding Configure button.

## GUI prototype features

- Main page contains movement mode, scheduled-tick mode, push-cost denominator, and adhesion mode controls.
- Block search uses a command-block-style suggestion dropdown, keyboard selection, and exact-match selection.
- Hitbox and Advanced adhesion open separate editor variants.
- Advanced editor exposes the geometry source, contact/depth thresholds, orientation/lead-direction controls, and six manual faces.
- Changed Blocks lists saved non-default entries and supports Focus/Revert.
- Settings save to `config/movable-block-entities/settings.properties` using a temporary file and atomic replacement where supported.

## Build

Minecraft 26.3's current Fabric toolchain requires Java 25. Build with:

```sh
./gradlew clean build
```

The build workflow compiles the project on pushes and pull requests and uploads the jar as a GitHub Actions artifact.

## Scope

This artifact is a navigable GUI/configuration prototype only. Saved values do not affect pistons, block movement, block entities, or adhesion in the world yet.

