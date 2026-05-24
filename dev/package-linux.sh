#!/usr/bin/env bash
# Builds Beacon for Linux using jpackage (app-image) and produces a tarball.
# Usage: package-linux.sh [version]
# Requires: JDK 17+, jpackage (bundled with JDK), curl, tar
set -euo pipefail

VERSION="${1:-0.2.0}"
VERSION="${VERSION#v}"

REPO="$(cd "$(dirname "$0")/.." && pwd)"
BUILD_DIR="$REPO/build"
PACKAGE_DIR="$BUILD_DIR/linux-package"
INPUT_DIR="$PACKAGE_DIR/input"
OUTPUT_DIR="$PACKAGE_DIR/output"
RELEASE_DIR="$BUILD_DIR/release"
LIB_DIR="$REPO/desktop/build/install/desktop/lib"

rm -rf "$INPUT_DIR" "$OUTPUT_DIR"
mkdir -p "$INPUT_DIR" "$OUTPUT_DIR" "$RELEASE_DIR"

if [ ! -f "$LIB_DIR/desktop.jar" ]; then
  echo "Running installDist first..."
  "$REPO/gradlew" --project-dir "$REPO" :core:test :desktop:test :desktop:installDist
fi

cp -r "$LIB_DIR"/. "$INPUT_DIR/"

"$REPO/dev/ensure-sing-box.sh" "$INPUT_DIR/sing-box"

ICON="$REPO/desktop/src/main/resources/icon.png"

jpackage \
  --type app-image \
  --name Beacon \
  --app-version "$VERSION" \
  --vendor Beacon \
  --description "VLESS Reality client" \
  --input "$INPUT_DIR" \
  --main-jar desktop.jar \
  --main-class app.beacon.desktop.BeaconDesktopKt \
  --icon "$ICON" \
  --dest "$OUTPUT_DIR" \
  --java-options "-Dfile.encoding=UTF-8"

TARBALL="$RELEASE_DIR/Beacon-Linux-v$VERSION.tar.gz"
tar -czf "$TARBALL" -C "$OUTPUT_DIR" Beacon

echo "$TARBALL"
