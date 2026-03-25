# Changelog

All notable changes to Share to Nostr are documented here.

## [v0.1.9] - Unreleased

### Fixed
- Fix `LogCollector.collectLogs()` blocking the main thread when tapping "Copy Logs" — offloaded to IO dispatcher.

### Added
- Add "Delete" button to Debug settings to clear the logcat buffer from within the app.

## [v0.1.8] - 2026-03-25

### Fixed
- Add missing ProGuard keep rules for youtubedl-android, Jackson, and Apache Commons to prevent crashes in release builds.

## [v0.1.7] - 2026-03-25

### Fixed
- Fix yt-dlp init crash by setting `extractNativeLibs=true` in the manifest for youtubedl-android.

## [v0.1.6] - 2026-03-25

### Added
- Add "Copy Logs" button to Settings screen for easier debugging of yt-dlp initialisation failures.

## [v0.1.5] - 2026-03-24

### Fixed
- Fix "yt-dlp instance not initialized" error by awaiting yt-dlp initialisation before attempting downloads.

## [v0.1.4] - 2026-03-23

### Fixed
- Fix backup rules lint errors; add data extraction rules for API 31+ compatibility.
- Derive `versionCode`/`versionName` from the git tag for correct Obtainium update detection.
- Add yt-dlp init retry logic to resolve persistent initialisation failures.

## [v0.1.1] - 2026-03-23

### Fixed
- Fix unresolved reference to `okio.buffer()` by adding the missing import.

## [v0.1.0] - 2026-03-23

### Added
- Initial release: Android app for sharing short videos (YouTube Shorts, TikTok, Instagram Reels, etc.) to Nostr via Blossom servers, signed with Amber.
