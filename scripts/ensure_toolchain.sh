#!/usr/bin/env bash
set -euo pipefail

TOOLCHAIN_ROOT="/home/user/.cache/4cbon2-android-toolchain"
JDK_DIR="$TOOLCHAIN_ROOT/jdk-17"
SDK_DIR="$TOOLCHAIN_ROOT/android-sdk"
GRADLE_CACHE="/home/user/.cache/4cbon2-gradle"
DL_DIR="/tmp/4cbon2-downloads"

mkdir -p "$TOOLCHAIN_ROOT" "$SDK_DIR" "$GRADLE_CACHE" "$DL_DIR"

# 1. Provision JDK 17.0.21+9
if [ ! -x "$JDK_DIR/bin/javac" ]; then
    echo "[toolchain] Downloading Temurin JDK 17.0.21+9..."
    JDK_ARCHIVE="$DL_DIR/jdk17.tar.gz"
    curl -fsSL "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.21%2B9/OpenJDK17U-jdk_x64_linux_hotspot_17.0.21_9.tar.gz" -o "$JDK_ARCHIVE"
    echo "8b9b611d917e3dfd8419d3566b5949226f69b052fed7e109c1368add1dfe8711  $JDK_ARCHIVE" | sha256sum -c -
    rm -rf "$JDK_DIR"
    mkdir -p "$JDK_DIR"
    tar -xzf "$JDK_ARCHIVE" -C "$JDK_DIR" --strip-components=1
    rm -f "$JDK_ARCHIVE"
fi

export JAVA_HOME="$JDK_DIR"
export ANDROID_HOME="$SDK_DIR"
export ANDROID_SDK_ROOT="$SDK_DIR"
export ANDROID_USER_HOME="$TOOLCHAIN_ROOT/android-user-home"
export GRADLE_USER_HOME="$GRADLE_CACHE"
mkdir -p "$ANDROID_USER_HOME"
export PATH="$JAVA_HOME/bin:$SDK_DIR/cmdline-tools/latest/bin:$SDK_DIR/platform-tools:$SDK_DIR/build-tools/36.0.0:$PATH"

# 2. Provision Android Command-Line Tools
if [ ! -x "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" ]; then
    echo "[toolchain] Downloading Android command-line tools..."
    CMD_ARCHIVE="$DL_DIR/cmdline-tools.zip"
    curl -fsSL "https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip" -o "$CMD_ARCHIVE"
    echo "e025545c62a8e64c7559119566a569fb1dec5f60  $CMD_ARCHIVE" | sha1sum -c -
    rm -rf "$SDK_DIR/cmdline-tools"
    mkdir -p "$SDK_DIR/cmdline-tools"
    unzip -q "$CMD_ARCHIVE" -d "$SDK_DIR/cmdline-tools"
    mv "$SDK_DIR/cmdline-tools/cmdline-tools" "$SDK_DIR/cmdline-tools/latest"
    rm -f "$CMD_ARCHIVE"
fi

# 3. Provision required Android SDK packages
if [ ! -f "$SDK_DIR/platforms/android-37.0/android.jar" ] || [ ! -x "$SDK_DIR/build-tools/36.0.0/apksigner" ]; then
    echo "[toolchain] Installing Android SDK packages..."
    yes | sdkmanager --sdk_root="$SDK_DIR" --licenses >/dev/null 2>&1 || true
    sdkmanager --sdk_root="$SDK_DIR" \
        "platform-tools" \
        "platforms;android-36" \
        "platforms;android-37.0" \
        "build-tools;36.0.0" >/dev/null
fi

echo "[toolchain] Ready:"
java -version 2>&1 | head -n 2
echo "ANDROID_SDK_ROOT=$ANDROID_SDK_ROOT"
ls -1 "$SDK_DIR/platforms"
