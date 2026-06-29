# Telegram TV Cast

Telegram TV Cast is an Android TV app scaffolded for Google Play distribution. It connects to the Telegram Bot API, watches an allowed bot chat or channel, and casts direct video links or Telegram video files onto the TV with D-pad friendly controls.

## How it works

1. Create a Telegram bot with BotFather and copy the bot token.
2. Add the bot to a private chat, group, or channel where you will send cast links.
3. Install the app on Android TV, enter the bot token, and optionally enter the allowed Telegram chat ID.
4. Send a direct MP4/HLS URL or upload a video file to the bot chat. The TV app polls Telegram and starts playback automatically.

## Current app features

- Android TV Leanback launcher support with a TV-first single-screen setup flow.
- Runtime bot token entry with basic Bot API token validation.
- Optional allowed chat ID filtering; leave it blank at first and the app displays the chat ID from incoming messages.
- Polling with persisted update offsets so old messages are not replayed after restart.
- Media3 ExoPlayer playback (MP4/HLS) with built-in TV transport controls and buffering/error reporting.
- Playback from direct HTTP(S) video URLs, Telegram `video` messages, video `document` messages, and animations.
- TV remote controls for play/pause/stop and Telegram commands for `/pause`, `/resume`, `/play`, and `/stop`.

## Building

```bash
./gradlew :app:assembleDebug      # debug APK -> app/build/outputs/apk/debug/
./gradlew :app:assembleRelease    # release APK -> app/build/outputs/apk/release/
```

Requires the Android SDK (set `sdk.dir` in `local.properties` or `ANDROID_HOME`),
JDK 17, and the bundled Gradle wrapper. CI builds the debug APK on every push/PR
via `.github/workflows/android.yml`.

## Configuration

### Optional default bot token

The default bot token is **not** stored in source. To have a build auto-load a
token (so you do not type it on the TV remote), add it to the git-ignored
`local.properties`, or set the `TELEGRAM_BOT_TOKEN` environment variable:

```properties
telegram.bot.token=123456789:your-bot-token
```

It is exposed through `BuildConfig.DEFAULT_BOT_TOKEN`. A clean checkout has an
empty value, so the token is entered at runtime instead. Rotate the bot in
BotFather (`/revoke`) if a token was ever committed or shared publicly.

### Release signing

Copy `keystore.properties.template` to the git-ignored `keystore.properties`
and fill in your keystore details (or set the `RELEASE_*` env vars). Without a
keystore, `assembleRelease` produces an unsigned APK.

## Google Play readiness notes

- The manifest declares Android TV Leanback support and the `LEANBACK_LAUNCHER` entry point.
- The app only requests `INTERNET`, which is required for Telegram polling and media playback.
- Vector launcher icon and gradient TV banner are included; swap in custom artwork before release if desired.
- Store bot credentials securely for production builds if expanding beyond this local TV prototype.
