# Runtime testing notes

## Main-branch test log

The supplied `Test logs.txt` is a HotSpot fatal-error log, not a Minecraft crash report or an ordinary Java exception. It records:

- Minecraft 26.3 running on Windows 11.
- Microsoft Build of OpenJDK `25.0.1+8`.
- G1 garbage collector.
- `-Xms512m -Xmx6144m`.
- The JVM aborted after about nine minutes.
- The failing assertion is in `jvm.dll`, at `g1HeapRegionManager.cpp:55`: `master free list MT safety protocol at a safepoint`.

That exact assertion is tracked by Microsoft in [microsoft/openjdk issue #677](https://github.com/microsoft/openjdk/issues/677). Microsoft traced it to a race in the Microsoft-specific G1 time-based heap-uncommit feature. The issue is separate from Minecraft's piston implementation and does not identify a CMBE class as the cause.

## Workaround for the affected Java runtime

For Microsoft OpenJDK 25.0.1, Microsoft documented disabling that feature with these JVM arguments:

```
-XX:+UnlockDiagnosticVMOptions -XX:-G1UseTimeBasedHeapSizing
```

Add them to the launcher's JVM-arguments field for the affected instance. Do not add them to the mod's Gradle build or to `settings.json`: JVM options must be applied before the game starts, and a mod cannot safely change them at runtime.

This option disables time-based return of idle G1 heap regions to the operating system. It does not disable G1 itself. Do not increase `-Xmx` just to address this particular assertion; the log is not an `OutOfMemoryError`.

## Retest procedure

1. Keep the same modpack, world, and memory allocation.
2. Apply the JVM flags above to the affected Microsoft OpenJDK 25.0.1 runtime.
3. Repeat the same gameplay session for longer than the previous nine-minute time-to-failure.
4. If the JVM remains stable, remove the flags only for a controlled comparison; do not treat one successful session as proof that CMBE piston behavior is correct.
5. For any later Minecraft-level failure, provide `latest.log` and the crash report as well as the JVM fatal-error file. They contain different diagnostic information.

## What this test does and does not establish

The supplied log establishes a native JVM/G1 failure signature that matches Microsoft's tracked issue. It does not establish whether any CMBE feature is working, nor whether a separate mod conflict exists in the modpack. The current GitHub Actions success only establishes that the current branch compiles and packages a JAR.

CMBE still needs in-game tests for movement modes, mixed vanilla/custom push-cost limits, block-entity persistence, support-dependent survival checks, group lifetime during landing, multiblock atomic landing, connected-pair landing, and scheduled-tick transfer/duplication.
