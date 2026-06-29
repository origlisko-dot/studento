# Telegram TV Cast

Telegram TV Cast is an Android TV app that plays videos you send in Telegram on
your TV — **without any per-user setup**. There is one public bot, run by the app
operator. The viewer just pairs their TV once (a 6-character code or a QR scan),
then sends any video link or video file to the bot and it plays on the big screen.

The Telegram bot token lives **only on the backend server** and is never shipped
in the app.

## Architecture

```
  Phone (Telegram)            Backend server                 Android TV app
  ----------------            --------------                 --------------
  send code / video  ───────▶  /telegram/webhook   ◀──────   long-poll /api/poll
                               pairs chat ⇄ device
                               resolves media URL
  watch on TV        ◀───────  /media/:id (proxy)  ◀──────   ExoPlayer plays URL
```

- **TV app** registers with the server, shows a pairing code + QR, then
  long-polls for commands and plays them full-screen with Media3 ExoPlayer.
- **Server** (`server/`) receives Telegram webhook updates for the one public
  bot, pairs each chat to a TV by code, and pushes `play`/`pause`/`resume`/`stop`
  commands. Telegram-hosted files are streamed through `/media/:id` so the bot
  token stays server-side.

## User flow

1. Open the app on Android TV — it shows a pairing code and a QR.
2. In Telegram, open the public bot (tap the QR or search its @username) and send
   the code (the QR deep link sends it for you via `/start`).
3. Send the bot any direct video URL (MP4/HLS) or a video file — it plays on the
   TV. Use `/pause`, `/resume`, `/stop` to control playback.

## Repository layout

| Path | What |
|------|------|
| `app/` | Android TV app (Java, Media3 ExoPlayer, ZXing for the pairing QR) |
| `server/` | Node/Express pairing + media-proxy backend for the public bot |
| `PRIVACY.md` | Privacy policy (required for Google Play) |
| `docs/STORE_LISTING.md` | Google Play listing copy + submission checklist |

## Building the app

```bash
./gradlew :app:assembleDebug      # debug APK   -> app/build/outputs/apk/debug/
./gradlew :app:assembleRelease    # release APK -> app/build/outputs/apk/release/
./gradlew :app:bundleRelease      # AAB (Play)  -> app/build/outputs/bundle/release/
```

Requires the Android SDK (`sdk.dir` in `local.properties` or `ANDROID_HOME`),
JDK 17, and the bundled Gradle wrapper. CI builds the debug APK on every push/PR
via `.github/workflows/android.yml`.

### App configuration

The app needs the backend base URL. It is **not** committed — provide it in the
git-ignored `local.properties` (or the `SERVER_URL` env var for CI):

```properties
server.url=https://your-server.example.com
```

It is exposed as `BuildConfig.SERVER_URL`. A clean checkout has an empty value,
so set it before building a distributable APK/AAB.

### Release signing

Copy `keystore.properties.template` to the git-ignored `keystore.properties` and
fill in your keystore details (or set the `RELEASE_*` env vars). Without a
keystore, `assembleRelease` produces an unsigned APK. See `RELEASE.md` for the
full publishing guide (keystore creation, AAB, Play Console steps).

## Running the server

See [`server/README.md`](server/README.md). In short:

```bash
cd server
npm install
BOT_TOKEN=... BOT_USERNAME=YourCastBot WEBHOOK_SECRET=... PUBLIC_URL=https://your-host npm start
npm run set-webhook    # register the Telegram webhook (same env vars)
```

## Google Play readiness

- Manifest declares Android TV Leanback support and the `LEANBACK_LAUNCHER` entry.
- The app requests only `INTERNET`.
- Vector launcher icon and gradient TV banner included.
- The bot token is never in the app — it lives only on the server.
- See `PRIVACY.md` and `docs/STORE_LISTING.md` before submitting.
