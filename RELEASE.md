# Release & publishing guide

End-to-end steps to publish Telegram TV Cast to Google Play.

## 1. Create the public bot (once)

1. In Telegram, talk to **@BotFather** → `/newbot` → choose a name and @username.
2. Copy the bot token. This goes on the **server only** (never in the app).

## 2. Deploy the backend

See `server/README.md`. Set `BOT_TOKEN`, `BOT_USERNAME`, `WEBHOOK_SECRET`,
`PUBLIC_URL`, deploy, then `npm run set-webhook`. Verify `GET /` returns
`{"ok":true}` and that messaging the bot a code pairs a running TV app.

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
