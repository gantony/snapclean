# SnapClean

A minimal Android app that wraps Snapchat's web interface, letting kids chat with friends without the addictive content feeds.

## Why

Snapchat is how kids talk to their friends. But Discover, Spotlight, and Stories are designed to keep them doom-scrolling. Snapchat's built-in parental controls (Family Center) can't disable these feeds. SnapClean strips them out by routing through the web version, which is chat-focused by design.

## What works

- Text chat and group chats
- Voice and video calls
- Sending photo snaps (front camera)
- Viewing friend Stories (read-only)
- Login session persists (~1 year)

## What's blocked

- **Discover** - third-party content, ads, publisher stories
- **Spotlight** - short-form video feed (TikTok-like)
- **Stories/Spotlight buttons** - visible but unclickable (touch overlay)
- **External links** - YouTube, TikTok, etc. sent in chat are silently dropped
- **Snap Map, Lenses, Snapchat+** - path-blocked

## How it works

Single-activity Android app with a WebView pointing at `web.snapchat.com`. Three layers of content blocking:

1. **Domain whitelist** - only `snapchat.com`, `snap.com`, `sc-cdn.net` navigations allowed
2. **Path blacklist** - `/discover`, `/spotlight`, `/stories`, `/map`, `/lens`, `/plus` blocked
3. **Touch overlay** - fixed div covers Stories/Spotlight buttons, repositions on rotate/keyboard

Additional spoofing to make the WebView work:
- Desktop Chrome user agent (Snapchat web requires desktop browser)
- `window.chrome` object injection (Snapchat detects WebView and redirects)
- `navigator.permissions.query` override (reports camera/mic as granted)

## Building

Requires JDK 17+ and Android SDK. No Android Studio needed.

```bash
./setup-sdk.sh          # one-time: downloads Android SDK (~1.5GB)
source env.sh
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Known limitations

- Back camera not available (web version limitation)
- No AR filters/lenses
- Camera picker shows 4 options on first use - pick any front-facing one
- Overlay positions are calibrated for a specific phone (Samsung, 1080x2340)
- Snapchat can change their web app at any time and break things
