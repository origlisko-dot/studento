# Google Play store listing — Telegram TV Cast

Draft copy and a submission checklist for the Android TV release. Adjust wording
and branding to taste before publishing.

## App details

- **App name:** Telegram TV Cast
- **Category:** Video Players & Editors
- **Form factor:** Android TV (also installable on phones/tablets)
- **Content rating:** Everyone (no user-generated public content; you play your
  own videos)

## Short description (≤ 80 chars)

> Send any video to a Telegram bot and watch it instantly on your TV.

## Full description

> **Cast videos to your TV straight from Telegram — no setup, no account.**
>
> Telegram TV Cast turns your Android TV into a screen for any video you have in
> Telegram. Pair once with a code or a quick QR scan, then just send a video link
> or a video file to the bot — it plays on the big screen instantly.
>
> **Why you'll like it**
> • No bot to create, no token to copy — pair in seconds.
> • Send direct MP4/HLS links or upload video files in Telegram.
> • Smooth playback powered by ExoPlayer, with full TV remote controls.
> • Control playback from your phone with /pause, /resume and /stop.
> • Designed for the 10-foot experience with D-pad friendly navigation.
>
> Open the app, scan the QR (or send the code) to the bot, and start casting.

## Required graphic assets

| Asset | Spec | Source in repo |
|-------|------|----------------|
| App icon (hi-res) | 512×512 PNG | `docs/play/icon-512.png` |
| Feature graphic | 1024×500 PNG | `docs/play/feature-1024x500.png` |
| TV banner | 1280×720 PNG | `docs/play/banner-1280x720.png` |
| TV screenshots (≥1, 1280×720 or 1920×1080) | PNG/JPG | `docs/play/screenshot-*.png` |

## Submission checklist

- [ ] Google Play Developer account ($25 one-time).
- [ ] Build a **signed** release **AAB** with your own keystore (see `RELEASE.md`).
- [ ] Set `server.url` to your deployed backend before building the release.
- [ ] Add the privacy policy URL to the listing — already live at
      `https://<your-server>/privacy`, no separate hosting needed.
- [ ] Complete the Data safety form: device ID + pairing code (app), chat ID +
      submitted media (service); transient, not sold, not for ads.
- [ ] Complete the content rating questionnaire.
- [ ] Opt the release into the **Android TV** form factor and pass TV review
      checks (D-pad navigation, banner present, leanback launcher).
- [ ] Upload screenshots, feature graphic, icon, and descriptions.
- [ ] Roll out to Internal testing first, then Production.

## Notes for review

- The single public bot is operated by the developer; users never create a bot.
- Users play their own content; the app is a personal media player/caster.
