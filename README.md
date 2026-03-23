# Share to Nostr

An Android app that lets you share short videos from YouTube Shorts, TikTok, Instagram Reels, and other platforms directly to Nostr.

When you find a short clip you like, tap **Share** in any app, select **Share to Nostr**, and the app will:

1. Download the video using [yt-dlp](https://github.com/yt-dlp/yt-dlp)
2. Upload it to your [Blossom](https://github.com/hzrd149/blossom) media server
3. Publish a [NIP-71](https://github.com/nostr-protocol/nips/blob/master/71.md) video event to your configured Nostr relays

All event signing is handled by [Amber](https://github.com/greenart7c3/Amber) (NIP-55), so your private keys never touch this app.

## How It Works

```
YouTube / TikTok / Instagram / X / Reddit / Facebook
        |
        | Share (text/plain URL)
        v
  Share to Nostr App
        |
        | 1. Extract video URL
        | 2. Download via yt-dlp
        | 3. Compute SHA-256
        | 4. Sign Blossom auth (kind 24242) via Amber
        | 5. Upload to Blossom server
        | 6. Build NIP-71 event (kind 34236)
        | 7. Sign video event via Amber
        | 8. Publish to Nostr relays
        v
  Video live on Nostr
```

## Supported Platforms

The app can download videos from any site supported by yt-dlp, including:

- YouTube Shorts
- TikTok
- Instagram Reels
- X / Twitter
- Reddit
- Facebook

## Features

- **Android Share Sheet integration** - appears as a share target in any app
- **Video download with progress** - configurable max resolution (480p / 720p / 1080p)
- **Blossom upload** - uploads to your self-hosted or preferred Blossom media server
- **NIP-71 video events** - publishes kind 34236 (short-form video) with proper `imeta` tags
- **Amber signing (NIP-55)** - all Nostr signing delegated to Amber, supports silent signing after first approval
- **Multiple servers and relays** - configure as many Blossom servers and Nostr relays as you want
- **yt-dlp runtime updates** - update the video downloader from within the app to keep up with platform changes

## Setup

### Prerequisites

- Android 8.0+ (API 26)
- [Amber](https://github.com/greenart7c3/Amber) installed for Nostr signing
- A Blossom media server (self-hosted or public)

### Build

1. Open the project in Android Studio (Ladybug 2024.2+)
2. Sync Gradle and let it download dependencies
3. Build and install on your device

### Configure

1. Open the app and go to Settings
2. Tap **Connect with Amber** to link your Nostr account
3. Add your Blossom server URL (e.g. `https://blossom.yourdomain.com`)
4. Verify the relay list (defaults: relay.damus.io, relay.nostr.band, nos.lol, relay.snort.social)
5. Set your preferred video quality

### Use

1. Find a short video on YouTube, TikTok, Instagram, etc.
2. Tap **Share** in that app
3. Select **Share to Nostr** from the share sheet
4. Optionally add a caption
5. Tap **Share to Nostr** and wait for download, upload, and publish to complete

## Self-Hosting a Blossom Server

If you want to host your own Blossom media server at home:

```bash
git clone https://github.com/hzrd149/blossom-server
cd blossom-server
npm install && npm run build
cp .env.example .env   # edit to configure
npm start
```

Put it behind a reverse proxy with HTTPS. The simplest option is [Caddy](https://caddyserver.com/):

```
blossom.yourdomain.com {
    reverse_proxy localhost:3000
    request_body {
        max_size 500MB
    }
}
```

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Video download | [youtubedl-android](https://github.com/yausername/youtubedl-android) (yt-dlp wrapper) |
| Nostr signing | [Amber](https://github.com/greenart7c3/Amber) via NIP-55 |
| Media upload | [Blossom](https://github.com/hzrd149/blossom) protocol |
| HTTP | OkHttp |
| Persistence | DataStore Preferences |

## Project Structure

```
app/src/main/java/com/sharetonostr/
  ShareToNostrApp.kt           Application class, initializes yt-dlp
  MainActivity.kt              Settings screen
  ShareReceiverActivity.kt     Share flow orchestrator
  download/
    UrlExtractor.kt            Parses video URLs from shared text
    VideoDownloader.kt          yt-dlp wrapper
  nostr/
    NostrEvent.kt              Event model with NIP-01 ID computation
    EventBuilder.kt            Builds NIP-71 and Blossom auth events
    AmberSigner.kt             NIP-55 Amber integration
    RelayPublisher.kt          WebSocket relay publishing
  blossom/
    BlossomClient.kt           Blossom upload with Nostr auth
  data/
    SettingsRepository.kt      DataStore preferences
  ui/
    screens/
      ShareScreen.kt           Share confirmation UI
      SettingsScreen.kt         Settings UI
    theme/
      Theme.kt                 Material 3 theme
```

## License

[MIT](LICENSE)
