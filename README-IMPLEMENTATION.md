# Implementation status

This branch is a draft Minecraft 26.3 Fabric implementation. Compilation and in-game regression tests are not yet verified.

## Environment and foundation

- Fabric Loom 1.19-SNAPSHOT, Java 25, Fabric Loader 0.20.1, Fabric API 0.165.0+26.3, Gradle 9.8.2 wrapper.
- GitHub Actions build workflow configured to compile and upload the mod JAR.
- Per-block settings saved atomically to `config/cmbe/settings.json`.
- Movement modes: Vanilla, Movable, Breakable, Immovable.
- Scheduled block tick modes: Vanilla, Transfer, Duplicate, using the vanilla scheduler collection.
- Fractional push denominator uses exact rational arithmetic; default budget is 12; extended movement is capped at 128 physical blocks.
- Movable block entities are serialized into moving piston entities and restored on regular / fast landing paths.
- Dark GUI with Block, Changed Blocks, Adhesion, Hitbox, Advanced pages; block search, configured hotkey, Mod Menu hook.

## Push groups and multiblock landing

- Every piston operation records members' original positions, destination positions, and intended states.
- During a member's landing and neighbour-shape update, synchronous state queries see the intended push-group arrangement and vacated source positions as air.
- Vanilla doors, beds, double plants and double chests are detected as two-part structures.
- Missing companions are included using the resolver's own block-line procedure, preserving its pushability and collision decisions.
- When one half lands, a still-moving partner in the same group is fast-landed synchronously.

## Adhesion

- Modes match the feature specification: Vanilla, Adhesive (all faces), Glazed (no faces), Hitbox and Advanced.
- Hitbox analysis writes to a separate `config/cmbe/adhesion-analysis-cache.json`.
- Analysis is scheduled incrementally on client title-screen ticks and server ticks; runtime adhesion reads the cache.
- Outline or collision geometry is analyzed for every possible state up to a 4096-state per-block bound.
- Each face uses a 16×16 grid, samples inward depths 1–16, and stores the largest fully occupied square using a dynamic-programming algorithm. Zero-volume boundary contact is excluded.
- Advanced mode provides a Direction-valued orientation-property selection and six independently configurable face switches. Manual adhesion never changes the block's actual orientation.
- Cache files include Minecraft version, property/value fingerprint, state count, analysis schema, and are written atomically.
- Blocks with context-dependent shapes fail closed and need a mod adapter. Automatic orientation for axis/front-and-top/rotation properties still needs adapters.

## Not yet established

- No GitHub Actions result confirming successful compilation has been returned.
- All mixin injection points/local captures, config GUI signatures, scheduled-tick semantics, and block-entity restore behavior need CI and in-game verification.
- Initial resolver-time support survival is not comprehensively projected; destination-aware survival is currently applied to the landing shape update.
- Modded multiblocks with more than two components need adapter registration.
- Block entity movement is an initial generic serialization route; mod-specific compatibility hooks and recovery on failed placements still need implementation and tests.
- No dedicated regression suite has yet validated doors/beds/chests, torches, rails, redstone timings, inventories, and fast landing order.
