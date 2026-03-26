#!/bin/bash
set -euo pipefail

SDK_ROOT="$HOME/Android/Sdk"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
CMDLINE_TOOLS_ZIP="/tmp/android-cmdline-tools.zip"

echo "=== SnapClean SDK Setup ==="

# Create SDK directory
mkdir -p "$SDK_ROOT/cmdline-tools"

# Download command-line tools if not present
if [ ! -d "$SDK_ROOT/cmdline-tools/latest" ]; then
    echo "Downloading Android command-line tools..."
    wget -q --show-progress -O "$CMDLINE_TOOLS_ZIP" "$CMDLINE_TOOLS_URL"
    echo "Extracting..."
    unzip -q "$CMDLINE_TOOLS_ZIP" -d "/tmp/android-cmdline-tools"
    mv "/tmp/android-cmdline-tools/cmdline-tools" "$SDK_ROOT/cmdline-tools/latest"
    rm -f "$CMDLINE_TOOLS_ZIP"
    rm -rf "/tmp/android-cmdline-tools"
    echo "Command-line tools installed."
else
    echo "Command-line tools already installed."
fi

export ANDROID_HOME="$SDK_ROOT"
export PATH="$SDK_ROOT/cmdline-tools/latest/bin:$SDK_ROOT/platform-tools:$PATH"

# Accept licenses
echo "Accepting licenses..."
yes | sdkmanager --licenses > /dev/null 2>&1 || true

# Install minimal SDK components
echo "Installing SDK components (platform 34, build-tools)..."
sdkmanager --install \
    "platforms;android-34" \
    "build-tools;34.0.0"

echo ""
echo "=== Done! ==="
echo "SDK installed at: $SDK_ROOT"
echo "Run 'source env.sh' before building."
