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
- Playback from direct HTTP(S) video URLs, Telegram `video` messages, video `document` messages, and animations.
- TV remote controls for play/pause/stop and Telegram commands for `/pause`, `/resume`, `/play`, and `/stop`.

## When the bot API token is needed

This prototype currently includes a prefilled bot token so the TV app can connect without typing it on the remote. For any real release, remove the default token, do not commit real bot tokens to source control, and rotate the bot in BotFather if the token was shared publicly.

## Google Play readiness notes

- The manifest declares Android TV Leanback support and the `LEANBACK_LAUNCHER` entry point.
- The app only requests `INTERNET`, which is required for Telegram polling and media playback.
- Replace the placeholder XML banner with production launcher/banner artwork before release.
- Store bot credentials securely for production builds if expanding beyond this local TV prototype.
