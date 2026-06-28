# Telegram TV Cast

Telegram TV Cast is an Android TV app scaffolded for Google Play distribution. It connects to the Telegram Bot API, watches an allowed bot chat or channel, and casts direct video links onto the TV with D-pad friendly controls.

## How it works

1. Create a Telegram bot with BotFather and copy the bot token.
2. Add the bot to a private chat or channel where you will send cast links.
3. Install the app on Android TV, enter the bot token, and optionally enter the allowed Telegram chat ID.
4. Send a direct MP4 or HLS URL to the bot chat. The TV app polls Telegram and starts playback automatically.

## Google Play readiness notes

- The manifest declares Android TV Leanback support and the `LEANBACK_LAUNCHER` entry point.
- The app only requests `INTERNET`, which is required for Telegram polling and media playback.
- Replace the placeholder XML banner with production launcher/banner artwork before release.
- Store bot credentials securely for production builds if expanding beyond this local TV prototype.
