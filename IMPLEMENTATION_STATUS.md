# Implementation status

This branch is the first implementation milestone for Minecraft 26.3 / Fabric. The adhesion feature family has deliberately been excluded: there are no adhesion settings, controls, screens, caches, or runtime hooks in this branch.

## Included in this milestone

- Fabric Loom 1.19.4 project targeting Minecraft 26.3, Java 25, and Gradle 9.8.0.
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

The GitHub Actions workflow uses Java 25 and Gradle 9.8.0. The build for commit `c805656f072e6ba843f67da72a4cccb382d811b0` completed successfully and uploaded the `cmbe-fabric-26.3` artifact (run `42331591916`). This verifies compilation and packaging only; it does not validate piston behavior in a running game.


## Main-branch test-log finding

The main-branch `Test logs.txt` reports a native JVM fatal error on Windows 11 with Microsoft OpenJDK 25.0.1+8. Its assertion is:

```
Internal Error (g1HeapRegionManager.cpp:55)
master free list MT safety protocol at a safepoint
```

This exact failure signature is documented in Microsoft OpenJDK issue [#677](https://github.com/microsoft/openjdk/issues/677). Microsoft identified a timing race in its G1 time-based heap-uncommit feature and documented this workaround for affected runtimes:

```
-XX:+UnlockDiagnosticVMOptions -XX:-G1UseTimeBasedHeapSizing
```

This is a JVM startup option, not a mod setting. The mod cannot safely set it from Java code. See [docs/TESTING.md](docs/TESTING.md) for the test-log analysis and retest procedure. The log does not show a CMBE exception, failed Mixin injection, or an application-level Java stack trace, so it does not justify changing piston hooks.
