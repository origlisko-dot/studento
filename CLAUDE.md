# Design system notes for this repo

This is a **native Android TV app** (Java, no XML layouts, no web stack) plus a
**Node/Express backend**. It does not use React, CSS, or a token pipeline, so
most generic "design system" tooling (Figma Code Connect, CSS variable
extraction, Storybook, etc.) does not apply directly. This doc describes the
actual equivalent in this codebase so design work can be translated correctly.

## 1. Token definitions

There is no token file/JSON and no transformation pipeline. Color "tokens"
exist in two places that are **not currently kept in sync** — treat the Java
constants as the source of truth, since the UI is built entirely in Java:

- `app/src/main/res/values/colors.xml` — only one color defined
  (`brand_blue = #229ED9`), used solely in `styles.xml`. Not used elsewhere.
- `app/src/main/java/com/studento/telegramcast/MainActivity.java` — the real
  palette, as `private static final int` hex constants (ARGB, e.g.
  `0xFF0B1B28`) plus inline hex literals scattered through the UI-building
  methods (`buildCard()`, `buildStep()`, etc.). Example:

  ```java
  private static final int COLOR_TEXT = 0xFFFFFFFF;
  private static final int COLOR_MUTED = 0xFF9FB9C8;
  private static final int DOT_WAITING = 0xFFF2B23E;
  private static final int DOT_LIVE = 0xFF43C463;
  private static final int DOT_ERROR = 0xFFE0584F;
  ```

  Palette in use (informal):
  - Background: `#0B1B28` (solid, screen), `#15252F` (card surface), `#0D1C28` (input field)
  - Border: `#213748` (card), `#24414F` (field)
  - Brand/accent: `#229ED9` (Telegram-ish blue), gradient `#FFD36A → #F2A93E` (PRO badge gold)
  - Text: `#FFFFFF` primary, `#9FB9C8` / `#8FA7B5` muted, `#C7D8E4` step text, `#EAF4FB` field/code text
  - Status dots: amber `#F2B23E` (waiting), green `#43C463` (live), red `#E0584F` (error)

  Spacing/sizing is **not tokenized** — every `dp(N)` call is a literal at the
  call site (see `dp()` helper, a simple px→dp converter). There is no spacing
  scale constant set.

- **No typography scale.** Text sizes are inline `setTextSize(N)` calls in sp
  (e.g. 26, 17, 15, 13). No type-scale enum/constants exist.

If asked to "extract tokens" or "apply Figma variables," the practical
mapping is: create named `private static final int` color constants (matching
the palette above) and, if it grows, a small `Dimens`/`TextSizes` holder class
— there's no XML resource pipeline actively used for values beyond what's
already in `colors.xml`/`strings.xml`.

## 2. Component library

There is no component library, no reusable custom Views, and no Compose. The
entire UI is built **imperatively** in `MainActivity.java` via small private
builder methods that construct and return standard Android widgets:

```java
private LinearLayout buildCard() { ... }
private LinearLayout buildStep(int number, String label) { ... }
private LinearLayout.LayoutParams stepParams() { ... }
private EditText buildField(ViewGroup parent, String label, String hint) { ... }
```

These are one-off, not extracted into a shared package, not documented, and
there's no Storybook/preview tooling (not applicable to Android TV anyway).
If a new screen/section is needed, the existing convention is: add another
`buildX()` private method that returns a `View`/`ViewGroup`, call it from
`buildUi()`/`buildCard()`, and wire any mutable views to instance fields set
at the top of the class.

There are exactly two "screens," both built inside `buildUi()`:
- **Setup/pairing** (`buildSetup()` → `buildCard()`): brand header, step list,
  pairing code, QR, status row, recently-played history, premium teaser.
- **Player** (`playerContainer`): full-screen `PlayerView` (Media3 ExoPlayer)
  with a "now playing" label overlay.

## 3. Frameworks & libraries

- **UI**: plain Android SDK widgets (`Activity`, `LinearLayout`, `FrameLayout`,
  `ScrollView`, `TextView`, `EditText`, `Button`, `ImageView`, `ProgressBar`).
  No Jetpack Compose, no XML layouts (`buildUi()` constructs everything in
  code) — the only XML resources are `drawable/*.xml` shapes/selectors and
  `values/strings.xml` / `values-iw/strings.xml`.
- **Playback**: `androidx.media3` (`media3-exoplayer`, `media3-exoplayer-hls`,
  `media3-ui` → `PlayerView`), declared in `app/build.gradle`.
- **QR codes**: `com.google.zxing:core` (`QRCodeWriter`, rendered to a
  `Bitmap` manually, pixel by pixel, then set on an `ImageView`).
- **Build system**: Gradle (wrapper checked in at `gradlew`/`gradle/wrapper/`),
  Android Gradle Plugin `com.android.application` 8.5.2, `compileSdk 35`,
  `minSdk 23`, Java 17 source/target compatibility. `BuildConfig` fields
  (`SERVER_URL`) are injected from `local.properties`/env at build time.
- **Backend** (`server/`): plain Node.js + Express (`express` only
  dependency), no frontend framework — it serves JSON APIs, proxies Telegram
  file downloads, and serves one static APK file. No CSS/HTML UI to speak of.

## 4. Asset management

- **Drawables only** — all visual assets are hand-written Android vector/shape
  XML in `app/src/main/res/drawable/`:
  - `ic_logo.xml`, `ic_launcher.xml` — original TV-screen + play-glyph mark
    (vector path data, not Figma-exported)
  - `banner.xml` — Android TV launcher banner (gradient + centered logo)
  - `bg_screen.xml`, `bg_card.xml`, `bg_field.xml`, `bg_logo.xml`, `bg_pro.xml`
    — solid/gradient `<shape>` backgrounds
  - `btn_primary.xml`, `btn_secondary.xml` — `<selector>` state-list drawables
    driving D-pad focus rings (`android:state_focused`)
  - `dot.xml` — small oval used as a tinted status indicator
- **No bitmap images** (no PNG/JPG/WebP assets), no CDN, no image
  optimization pipeline — everything renders at runtime as vector/shape XML
  or programmatically-drawn bitmaps (the QR code).
- The repo also has a **prebuilt distributable APK checked into git** at
  `server/public/telegram-tv-cast.apk`, served by the backend at `GET /app`
  for sideloading via the Downloader app — this must be regenerated
  (`./gradlew :app:assembleDebug` then copy) after any app change that should
  reach existing installs via that link.
- `docs/play/` holds Google Play store-listing graphics (icon, feature
  graphic, banner, screenshots) generated separately for the store listing —
  these are NOT used by the app at runtime.

## 5. Icon system

There is no icon font/sprite system and no third-party icon library. Exactly
one icon glyph exists, defined as inline SVG-style path data inside
`ic_logo.xml` (a TV outline + play triangle + stand), reused at different
scales:
- `ic_logo.xml` — standalone glyph, tinted white, used inside the `bg_logo`
  circle/square on the setup card.
- `ic_launcher.xml` — the same path data wrapped in a `<group>` with a scale/
  translate transform, composited over the rounded-square brand-blue
  background, used as the app's launcher icon.

No naming convention exists beyond `ic_<name>` (only one icon exists). If more
icons are added, follow that prefix and keep them as `<vector>` XML (not
bitmap) for TV-density flexibility.

## 6. Styling approach

There is no CSS/CSS-in-JS/styled-components — "styling" means setting
properties directly on widget instances in Java (`setTextColor`,
`setTextSize`, `setBackgroundResource`, `setPadding`, `LayoutParams`).

- `app/src/main/res/values/styles.xml` defines exactly one theme,
  `AppTheme` (parent `Theme.Material.NoActionBar`), applied via the manifest;
  it only sets `fontFamily`, hides the title/action bar, and sets
  `colorAccent`. It is **not** used for typography or component styling
  beyond that.
- All real styling is inline, per-widget, inside the `buildX()` methods in
  `MainActivity.java` — there is no shared style/theme object passed around.
- **Responsive design**: none in the adaptive/breakpoint sense. The layout is
  built once for a 10-foot TV viewport using `dp()`-converted fixed
  dimensions and `LinearLayout` weights (`layout_weight`) for flexible
  regions; there's deliberate "overscan-safe" padding (`outer.setPadding(...)`
  in `buildSetup()`) to keep content clear of real TV edge cropping. The same
  layout also happens to run on phones (it's sideloadable there) but nothing
  is conditioned on screen size/density.
- **Localization**, not CSS, drives most "theming" variation: `values/
  strings.xml` (English, default) and `values-iw/strings.xml` (Hebrew) are
  swapped automatically by the OS locale; Hebrew strings rely on Android's
  built-in RTL handling (`android:supportsRtl="true"` in the manifest), there
  is no custom RTL/LTR styling logic.

## 7. Project structure

```
app/
  build.gradle                  # AGP plugin, compileSdk/minSdk, BuildConfig.SERVER_URL,
                                 # signingConfigs (keystore.properties-driven), media3+zxing deps
  src/main/
    AndroidManifest.xml         # single Activity, LEANBACK_LAUNCHER intent filter
    java/com/studento/telegramcast/
      MainActivity.java         # the entire app: UI construction, networking, playback,
                                 # pairing/QR, history, premium gating — one Activity, no other classes
    res/
      drawable/                 # all visual assets (see §4)
      values/ , values-iw/      # strings only (+ one unused color, one theme)
server/
  index.js                      # Express app: pairing, Telegram webhook, media proxy, /app APK download
  public/telegram-tv-cast.apk   # checked-in build artifact served for sideloading
docs/
  play/                         # Google Play store-listing graphics (not used by the app)
  STORE_LISTING.md
render.yaml, Procfile           # Railway/Render deploy config for server/
RELEASE.md, PRIVACY.md          # publishing docs
```

**Feature organization**: there isn't any — this is a single-Activity,
single-file Android app (~700 lines) plus a single-file backend. New
"features" are added as new private methods/fields on `MainActivity` (UI) or
new routes/handlers in `server/index.js` (backend), not as separate
modules/packages. If the app grows meaningfully, the first refactor worth
doing is splitting `MainActivity.java` into a UI-builder class + a
networking/pairing class, since everything currently lives in one place.

## Practical guidance for Figma → code work here

- Do not look for or generate CSS variables, Tailwind config, or a `tokens.json`
  — there is none, and one should not be invented without being asked.
  Match new colors/spacing to the literal Java constants/values listed in §1.
- Do not propose React/Vue components or Storybook stories — translate Figma
  frames into new `buildX()` Java methods following the existing pattern in
  `MainActivity.java`, using the same widget types, `dp()` helper, and
  drawable-based backgrounds.
- New icons/logos must be hand-authored as Android `<vector>` XML (matching
  the style of `ic_logo.xml`), not linked as PNG/SVG file references.
- Any new color must be added as both a named Java `int` constant near the
  top of `MainActivity.java` (for consistency with existing code) — don't
  scatter new hex literals deep in builder methods if avoidable.
- After any visual change, rebuild the debug APK and refresh
  `server/public/telegram-tv-cast.apk` (see §4) so the hosted sideload link
  stays current; this has been the standing workflow in this repo.
