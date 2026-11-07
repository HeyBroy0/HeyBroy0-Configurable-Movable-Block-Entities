# GUI-only prototype status

This branch is a client-side GUI and design prototype. It is intentionally separate from the piston implementation branch.

## Included

- Fabric 26.3 client mod scaffolding and CI build workflow.
- The dark configuration screen and its block identifier/name search.
- The `O` hotkey and Mod Menu entry for opening the screen.
- Preview controls for movement mode, scheduled-tick mode, and push-cost denominator.
- A saved-preview list with Edit and Reset actions.
- Local JSON persistence for preview settings under `config/cmbe/settings.json`.

## Explicitly not included

- No piston mixins or piston structure resolver hooks.
- No changes to vanilla push, pull, retract, break, survival, or landing behavior.
- No block-entity movement, multiblock handling, scheduled-tick processing, push-cost calculation, or adhesion runtime.
- No setting in this branch affects the game world. Saving a value only stores it for the GUI preview.

The visible controls are interactive so the screen can be tested and its layout refined independently. They are not a working implementation of the movement features.

## Build

The branch targets Minecraft 26.3, Fabric Loader 0.20.1, Fabric API 0.165.0+26.3, Java 25, and Gradle 9.8.0. GitHub Actions runs `./gradlew --no-daemon clean build` and uploads the resulting JAR.
