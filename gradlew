#!/usr/bin/env sh
set -eu

# GitHub Actions supplies Gradle through setup-gradle. The fallback bootstraps
# the pinned distribution for local environments without a Gradle install.
if command -v gradle >/dev/null 2>&1; then
    exec gradle "$@"
fi

GRADLE_VERSION="9.6"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
DIST_DIR="$GRADLE_USER_HOME/wrapper/dists/gradle-$GRADLE_VERSION-bin/cmbe-bootstrap"
GRADLE_BIN="$DIST_DIR/gradle-$GRADLE_VERSION/bin/gradle"

if [ ! -x "$GRADLE_BIN" ]; then
    mkdir -p "$DIST_DIR"
    ZIP_FILE="$DIST_DIR/gradle-$GRADLE_VERSION-bin.zip"
    command -v curl >/dev/null 2>&1 || {
        echo "Gradle is not installed and curl is unavailable." >&2
        exit 1
    }
    curl --fail --location --retry 3 "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" --output "$ZIP_FILE"
    command -v unzip >/dev/null 2>&1 || {
        echo "The unzip command is required to bootstrap Gradle." >&2
        exit 1
    }
    unzip -q -o "$ZIP_FILE" -d "$DIST_DIR"
fi

exec "$GRADLE_BIN" "$@"
