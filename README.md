# Void Web

<img src="art/web-icon.svg" width="160" align="right" alt="Void Web logo">

A web browser for Android (10+) from the VOID family (VOID Portal, Void Browse, Void Weather…), built on
Mozilla's Firefox engine (GeckoView). Kotlin + Jetpack Compose; GeckoView is the only library outside AndroidX.

The logo is a throwback to the old fox-and-globe browser icons: a deep-space globe holding the VOID spiral, with a
flame-fox curled round it in the theme's colours (drawn from scratch in `art/gen_icon.py`, no Mozilla artwork).

## Features
- Firefox's engine with Strict tracking protection, Global Privacy Control, query-parameter stripping, optional
  HTTPS-only mode, and **uBlock Origin installed from addons.mozilla.org on first launch**.
- Firefox add-ons: "Add to Firefox" on addons.mozilla.org works; Add-ons screen to switch them on/off, allow them in
  private tabs, open their settings, remove them.
- Tabs with page thumbnails, private tabs, tabs reopen after a restart (not private ones), undo for closed tabs.
- Address bar that searches or opens addresses, with bookmark and history suggestions; six search engines.
- VOID-styled new-tab page (most-visited sites, bookmarks), error pages, and every page dialog: alerts, dropdowns,
  date/time and colour pickers, sign-in, permissions (location, camera, microphone…), long-press menus.
- Downloads to the phone's Downloads folder, find in page, desktop site, share, open in another app, full-screen video.
- The VOID themes (shared with VOID Portal and Void Browse), launcher icon per theme, living sky, opening animation
  (the fox races round the globe), sounds. Websites with a dark design follow the theme.
- Can be set as the default browser; takes links and shared text from other apps.
- **In-app updates** (Settings › Updates) from this repository's GitHub releases.

## Building
```
./build.sh                          # release APK → VoidWeb.apk (arm64 phones)
./build.sh assembleDebug -Pabi=x86_64   # for the emulator
./build.sh testDebugUnitTest -Pabi=x86_64
```
The first build downloads Gradle 9.7, the Android plugin and GeckoView (~100 MB) into `.gradle-home`; add
`--offline` afterwards. GeckoView 158 needs compileSdk 37.2, which the build fetches into the SDK if missing.
Versions live in `version.properties` (`versionCode`, `versionName`, `gecko` = the GeckoView version).

Regenerate art with `python3 art/gen_icon.py` (icons, per-theme launcher aliases in the manifest) and
`python3 art/make_sounds.py app/src/main/res/raw`.

## Releases and updates
`.github/workflows/release.yml` runs every day at 07:17 UTC. When Mozilla has published a newer GeckoView release
(the 4-weekly majors and the security dot-releases in between), it bumps `version.properties`, commits, builds a
signed APK, and publishes a GitHub release with `VoidWeb.apk` and `latest.json` (version, SHA-256, notes). The app
checks the latest release every 6 hours and on demand in Settings; tapping **Update** downloads it, checks the
SHA-256 and installs it. Android asks to confirm the first time; after that Void Web updates itself quietly.

To release app changes: `./release.sh "What changed"` (bumps the version and pushes; the workflow does the rest).
"Run workflow" on GitHub (with *force*) rebuilds the current version.

**Signing.** Every APK must be signed with the same key or Android refuses the update. The key is
`~/.config/void-web/void-web.jks` (password in `~/.config/void-web/keystore.properties`, symlinked here and
git-ignored). Back both up. The workflow gets them from the repository secrets `KEYSTORE_B64`
(`base64 -w0 ~/.config/void-web/void-web.jks`) and `KEYSTORE_PASSWORD`.

The updater reads releases through GitHub's public API, so it needs the repository to be public (or another update
source; see `UPDATE_REPO` in `app/build.gradle.kts`).
