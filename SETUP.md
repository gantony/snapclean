# SnapClean - Setup Guide

Minimal Android WebView app that wraps web.snapchat.com, giving your kid
chat + friend Stories without Discover, Spotlight, or ads.

## Prerequisites (already installed)

- JDK 21 (OpenJDK)
- ADB (Android Debug Bridge)

## Step 1: Install Android SDK command-line tools

Run the setup script (downloads ~150MB, installs ~1.5GB total):

```bash
./setup-sdk.sh
```

This will:
- Download Android command-line tools
- Install SDK platform 34, build-tools 34.0.0
- Accept all licenses

## Step 2: Build the APK

```bash
source env.sh
./gradlew assembleDebug
```

The APK will be at: `app/build/outputs/apk/debug/app-debug.apk`

## Step 3: Install on the phone

1. Enable USB debugging on the Android phone:
   - Settings > About Phone > tap "Build number" 7 times
   - Settings > Developer options > enable "USB debugging"
2. Connect phone via USB, authorize the computer on the phone
3. Install:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Step 4: Lock down the phone

After installing SnapClean, use Google Family Link (or similar) to:
- Disable/hide the native Snapchat app
- Prevent installing new apps without approval
