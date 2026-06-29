# Release & publishing guide

End-to-end steps to publish Telegram TV Cast to Google Play.

## 1. Public bot (done)

The public bot is **@TV_TO_TELEGRAMbot** (display name TV_GRAM). Keep its token
private — it goes on the **server only**, never in the app. To rotate it, use
`/revoke` in @BotFather and update `BOT_TOKEN` on the server.

## 2. Deploy the backend

Easiest path — **Render Blueprint** (see `server/README.md`): New → Blueprint →
pick this repo → set `BOT_TOKEN` → Deploy. `BOT_USERNAME=TV_TO_TELEGRAMbot` is
prefilled, `WEBHOOK_SECRET` is auto-generated, `PUBLIC_URL` is auto-detected, and
the webhook registers itself on startup.

Verify `GET /` returns `{"ok":true}`, then message the bot the code shown on a
running TV to confirm pairing. Copy the deployed URL into the app's `server.url`.

## 3. Create your upload keystore (once)

Use the helper script (keep the generated file and passwords safe and private):

```bash
./scripts/generate-keystore.sh
```

This creates `release.keystore` and prints the values to put in the git-ignored
`keystore.properties`:

```properties
storeFile=release.keystore
storePassword=********
keyAlias=telegramtvcast
keyPassword=********
```

> Back up the keystore and passwords. If you lose them you cannot update the app
> on Google Play. Consider enabling Play App Signing in the Console.

## 4. Build the release artifact

Point the app at your deployed backend, then build the AAB Google Play wants:

```properties
# local.properties (git-ignored)
server.url=https://your-server.example.com
```

```bash
./gradlew :app:bundleRelease
# -> app/build/outputs/bundle/release/app-release.aab
```

(Use `./gradlew :app:assembleRelease` for a signed APK to sideload/test.)

## 5. Google Play Console

1. Create the app (Android TV form factor).
2. Upload `app-release.aab` to Internal testing.
3. Fill store listing from `docs/STORE_LISTING.md` and upload graphics from
   `docs/play/`.
4. Add the privacy policy URL (host `PRIVACY.md`), complete Data safety and
   content rating.
5. Test on a real Android TV, then promote to Production.

## Versioning

Bump `versionCode` (integer, must increase every upload) and `versionName` in
`app/build.gradle` for each release.
