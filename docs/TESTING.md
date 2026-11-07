# Runtime testing notes

## Current test log

The latest `Test logs.txt` on `main` is a short issue list, not a JVM fatal-error log. It currently reports:

1. Blocks configured as MOVABLE can be pushed but are not pulled back by sticky pistons.
2. Blocks that are normally breakable can still be destroyed when a moving block enters their position, even after they are configured as MOVABLE.
3. Continue with the remaining implementation work.

The earlier Microsoft OpenJDK G1 fatal error is not present in this latest log. It is being treated as a historical, currently unreproduced issue and is not the focus of this development pass.

## Reproduction details still needed

The two piston issues are actionable, but the log does not include exact block IDs or compact world layouts. For each issue, record:
- The block ID and its configured movement mode.
- Whether the block is directly in front of the piston or part of a sticky/slime/honey group.
- Piston facing and whether the operation is extension or retraction.
- Whether the failure occurs with vanilla blocks alone or only with a modded block.
- The expected result and the observed result.

This helps distinguish resolver selection from the moving-block landing path.

## Verification limits

GitHub Actions verifies compilation and packaging only. It does not execute a Minecraft world or validate piston behavior. Each behavior change still needs an in-game test with the exact Minecraft 26.3 / Fabric runtime.

The earlier JVM issue can be revisited if it recurs. If it does, preserve the JVM fatal-error file separately from Minecraft's `latest.log` and crash report.
