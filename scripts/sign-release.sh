#!/usr/bin/env bash
# Run from VS Code's terminal; apksigner prompts for the keystore password locally.
set -euo pipefail

AMAAL_PROJECT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$AMAAL_PROJECT_DIR"
AMAAL_SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
AMAAL_BUILD_TOOLS="${AMAAL_BUILD_TOOLS:-$AMAAL_SDK_DIR/build-tools/35.0.0}"
AMAAL_KEYSTORE="${AMAAL_KEYSTORE:-$AMAAL_PROJECT_DIR/amaal-release.jks}"
AMAAL_KEY_ALIAS="${AMAAL_KEY_ALIAS:-amaal}"
AMAAL_RELEASE_DIR="$AMAAL_PROJECT_DIR/app/build/outputs/apk/release"

if [[ ! -f "$AMAAL_KEYSTORE" ]]; then
    echo "Keystore not found: $AMAAL_KEYSTORE" >&2
    echo "Set AMAAL_KEYSTORE to your existing keystore path." >&2
    exit 1
fi
for AMAAL_TOOL in zipalign apksigner; do
    if [[ ! -x "$AMAAL_BUILD_TOOLS/$AMAAL_TOOL" ]]; then
        echo "Missing tool: $AMAAL_BUILD_TOOLS/$AMAAL_TOOL" >&2
        echo "Set AMAAL_BUILD_TOOLS to your Android SDK build-tools directory." >&2
        exit 1
    fi
done

./gradlew :app:assembleRelease
AMAAL_UNSIGNED_APK="$AMAAL_RELEASE_DIR/app-release-unsigned.apk"
if [[ ! -f "$AMAAL_UNSIGNED_APK" ]]; then
    echo "Release build did not produce $AMAAL_UNSIGNED_APK" >&2
    exit 1
fi

"$AMAAL_BUILD_TOOLS/zipalign" -f -P 16 4 \
    "$AMAAL_UNSIGNED_APK" "$AMAAL_RELEASE_DIR/app-release-aligned.apk"
"$AMAAL_BUILD_TOOLS/apksigner" sign \
    --ks "$AMAAL_KEYSTORE" --ks-key-alias "$AMAAL_KEY_ALIAS" \
    --out "$AMAAL_RELEASE_DIR/Amaal-Tracker-release.apk" \
    "$AMAAL_RELEASE_DIR/app-release-aligned.apk"
"$AMAAL_BUILD_TOOLS/apksigner" verify --verbose --print-certs \
    "$AMAAL_RELEASE_DIR/Amaal-Tracker-release.apk"
echo "Verified APK ready to share: $AMAAL_RELEASE_DIR/Amaal-Tracker-release.apk"
