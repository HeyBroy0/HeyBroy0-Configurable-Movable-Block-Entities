# Implementation status

This branch is the first implementation milestone for Minecraft 26.3 / Fabric. The adhesion feature family has deliberately been excluded: there are no adhesion settings, controls, screens, caches, or runtime hooks in this branch.

## Included in this milestone

- Fabric/Loom Gradle project targeting Minecraft 26.3 and Java 25.
- Fabric metadata, client/common entrypoints, Mixin configuration, and a build workflow.
- Atomic JSON persistence at `config/cmbe/settings.json`.
- Dark configuration screen, registry-ID/display-name search, keyboard suggestion navigation, changed-block list, Focus/Revert actions, and a configurable keyboard shortcut.
- Mod Menu config-screen integration.
- Initial piston movement policy hooks for configured immovable and movable blocks, plus breakable-block handling in the structure resolver.
- Exact-rational push-cost accounting for blocks explicitly configured as movable. Duplicate positions are counted once; vanilla movement keeps vanilla push-limit behavior.

## Deliberately incomplete runtime work

These settings/UI values are persisted, but their runtime behavior is not implemented in this milestone:

- Scheduled-tick transfer and duplication.
- Block-entity serialization/restoration. To avoid inventory or machine-data loss, a block configured as movable is not admitted to the custom movement path while it has a block entity.
- Per-operation piston movement groups and destination-aware survival checks.
- Structure adapters and coordinated multiblock landing.
- Full compatibility adapters for third-party block entities and modded structures.

The next development pass must implement these against the exact 26.3 mapped bytecode and validate them in a running game. In particular, it must not assume that an older Minecraft injection point remains present.

## Build verification

The GitHub Actions workflow uses Java 25 and Gradle 9.6 and uploads the built JAR. A successful source commit is not itself a successful game/build test; consult the workflow result for this branch before treating the artifact as validated.
