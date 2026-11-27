# HeyBroy0 – Configurable Movable Block Entities

## Project objective

Create a buildable Minecraft Fabric mod for **Minecraft 26.3** that allows configurable piston movement of block entities and multiblock structures.
The mod should extend vanilla piston behavior while preserving vanilla mechanics wherever possible.
The project already uses FrozenLib. Use it where appropriate and integrate properly, and avoid adding unnecessary dependencies.
The mod must build through GitHub Actions.

UX instruction details in readmeUX.md (necessary only for changing the gui)
Hitbox and Advanced adhesion settings Advanced.md (not necessary for now)

**Overall design principle:**
Extend vanilla piston movement without changing vanilla behavior. Blocks in the same movement group should be able to survive when moved together, block entities, multiblock structures, scheduled ticks, and redstone behavior remain predictable and compatible.

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

## Create the piston movement implementations according to the settings.
### Movement options:
- **Vanilla:** Preserve the block's existing vanilla piston behavior. (Default)
- **Movable:** Allows the block to be movable, including support for block entities, even when vanilla would normally prevent movement.
- **Breakable:** Destroy the block when a piston attempts to move it, following appropriate vanilla destruction and drop behavior.
- **Immovable:** Prevent piston movement and destruction of the block.

### Scheduled-tick behavior
Scheduled-tick handling must be configurable per block and not invasive using Minecraft's existing tick scheduler.
Perform tick operations exactly once per movement transaction.
- **VANILLA** Default. tick behavior is not touched
- **TRANSFER** Move eligible pending block ticks from the original position to the destination, preserving their original trigger time, remaining delay, priority, and ordering as closely as the vanilla scheduler permits. Applied after the block lands.
- **DUPLICATE** Leave the original scheduled tick untouched and create a copy pending tick for the destination similar to transfer.

If the relevant scheduler operation requires internal access, investigate the 26.3 implementation and use the smallest appropriate Mixin or access widener.

### Define push cost
Push cost represents how much of the vanilla piston push capacity a block consumes.
The setting is represented by a positive integer denominator. The default is 1.
Be careful of floating point error

- Accept positive nonzero integers only.
- Count each physical block only once. Multiblock like doors and beds add 2 physical blocks.
- Preserve vanilla limits for blocks configured to use vanilla movement behavior. (ignore push cost)

### Adhesion
- **Vanilla:** No change to the block's adhesion.
- **Adhesive:** All sides of the block can be pulled and moved with slime.
- **Glazed:** None of the sides can be pulled or moved with slime, can still be directly pushed.
- **Hitbox:** Stickiness based on automatic factors of the block - Do not Implement functionality.
- **Advanced:** Configurable face stickiness based on properties - Do not Implement functionality.
---

### Implement piston push groups
Create a performant temporary movement context for every piston push or retraction operation that follows every block being moved by that single piston operation including piston-head creation or retraction and if piston base state change.
The context must remain available throughout the movement and landing process, including relevant sub-tick block events such as the very start of movement.
Other piston push groups do not have relation to each other.

### Group Preservation
Prevent blocks from failing survival checks before and during movement. Add a 'moving' tag that causes all survival checks to pass that is applied to all blocks in a piston push group right after a movement is successfully resolved until the end when the very last block in that group has landed.

### Fast landing
Some blocks may land faster than the normal 3-tick cooldown. (the one block ahead of a sticky piston that is powered for less than 3 ticks)
When this happens:
- Remove it from the piston push group once its actual destination state is established.
- If it belongs to a multiblock structure, move it and its companion out of their original piston push group into their own personal group then coordinate their landing.
Make landing operations idempotent so multiple update paths cannot cause duplication or corruption.

### Coordinated landing
When a multiblock component lands:
- Its required companions must land in the same moment through a coordinated landing operation.
- Put them into a unique piston push group to prevent survival check failure until all companions are landed.
- Perform the operation synchronously and prevent unrelated block updates from interrupting partial structure placement.
- Ensure the complete structure is placed before ordinary neighbor notifications where possible.
- Avoid duplicate placement, duplicated block entities, or multiple landing operations for the same component.
This requirement applies to the multiblock and its companions, not automatically to every block in the entire piston push group (double chests and shelves should not coordinate).

### Move block entities safely
Support moving blocks containing block entities and persistent data.
Examples include:
- Chests and inventories.
- Furnaces and processing machines.
- Modded machines.
- Other data-storing blocks.

Requirements:
- Preserve inventories and do not duplicate or drop inventories
- Preserve machine progress and relevant timers.
- Preserve persistent custom data.
- Restore data at the correct destination.
- Avoid duplicate or invalid block entities.
- Handle failed movement safely.
- Use the current Minecraft 26.3 serialization APIs.

---

## Compatibility and mod developer support
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

Do not invent dependency versions, Maven coordinates, Java versions, or loader requirements.
Verify all of them against the actual project and available 26.3 Fabric releases.
Do not require release secrets for normal CI builds.

Do not silently leave a feature partially implemented while describing it as complete.

---

## Common bugs/mistakes:
We assumed an ordinary Java interface could safely live inside the same package as Mixin classes. Mixin restricts normal classloading from packages designated for Mixins, so Minecraft rejected the interface when other code tried to reference it.
We assumed Minecraft 26.3's PistonMovingBlockEntity.finalTick() contained a Level.setBlock() invocation at the expected injection point. The actual method bytecode did not contain a matching invocation at that location, so Mixin found zero targets and aborted startup.
We assumed the same Level.setBlock() invocation would be available for MixinExtras to wrap inside finalTick(). The transformed method did not expose that invocation as expected, so MixinExtras could not install the wrapper.
The recurring issue is that we were making assumptions about Minecraft's internal bytecode rather than verifying the actual 26.3 implementation before writing injection points. We should inspect the target methods in the exact Minecraft version and mappings we're building against before choosing Mixin injection strategies.
Assumed Minecraft 26.3 registry entries used Identifier directly.	Updated code to use ResourceKey<Block> and retrieve its identifier through .identifier().
Assumed ResourceKey exposed getPath().	Changed to .identifier().getPath().
Assumed Gradle could access project inside the JAR task's execution-time closure with configuration caching enabled.	Removed the incompatible custom JAR license-renaming logic.
Assumed Gradle downloads would succeed without retries.	Increased the network timeout to 30 seconds and enabled three retries.
Moving blocks are immovable
