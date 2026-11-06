# Implementation status

This branch establishes the Minecraft 26.3 Fabric project and staged implementation.

## Foundation and settings

- Java 25, Fabric Loader 0.20.1, Fabric API 0.165.0+26.3, Loom 1.19-SNAPSHOT and Gradle 9.8.2 wrapper.
- GitHub Actions build workflow with a mod-JAR artifact.
- Per-block settings persisted atomically in `config/cmbe/settings.json`.
- Per-block Vanilla / Movable / Breakable / Immovable piston reactions.
- Movable block-entity payload stored on the moving piston block entity and restored on normal and fast landing.
- Scheduled-tick Vanilla / Transfer / Duplicate modes through the vanilla block tick collection.
- Exact rational push-cost accounting, vanilla budget 12, bounded 128-block resolver ceiling.
- Adhesion Vanilla / All / None; Hitbox and Advanced still fall back to vanilla.
- Dark configuration UI with Block, Changed, Adhesion, Hitbox, and Advanced pages and a configurable open-menu key.
- Mod Menu config-screen entrypoint. Hitbox/Advanced sliders remain display-only.

## Piston push groups and multiblocks

- Push operations record original and destination positions plus final block states.
- During a member's landing transaction, group participants see their intended destination arrangement and vacated source positions as air.
- Vanilla doors, beds, tall plants, and paired chests are resolved as complete two-part structures. A structure move is rejected if its companion cannot move or its destination is obstructed.
- When one supported multiblock component lands, a still-moving companion in the same push group is fast-landed synchronously.
- Group tracking is reduced as members land; projection is not active for unrelated world queries outside the placement transaction.

## Not yet complete

- Modded multiblock adapter registration and structures with more than two components.
- Push-group survival projection during every initial resolver check; the current projection is scoped to landing.
- HITBOX shape/orientation analysis, connected-square/depth measurement, geometry cache, and the functional Advanced face editor.
- Automated in-game regression tests, especially for block entities, tick timing, support-order permutations, and piston redstone circuits.
- CI is configured, but a successful workflow run has not yet been returned by GitHub; compilation/runtime compatibility is not claimed until that check completes.
