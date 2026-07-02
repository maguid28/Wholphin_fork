# Ndorfin — Android TV client for Jellyfin

Ndorfin is an open-source Android TV client for Jellyfin, based on [Wholphin](https://github.com/damontecres/Wholphin). It provides a Plex-inspired UI and supports playback with ExoPlayer or MPV.

<p align="center">
<a href="https://github.com/maguid28/Wholphin_fork/releases">
<img alt="Current Release" src="https://img.shields.io/github/v/release/maguid28/Wholphin_fork?label=release"/>
</a>
</p>

## Highlights

- Customizable Home rows, navigation drawer, and library browsing
- Dual subtitles during playback
- Jellyseerr/Seerr Discover integration
- Library TV guide with picture-in-picture
- Live TV & DVR, trickplay, refresh-rate switching
- Metadata rematch and delete from long-press menus
- ExoPlayer (with optional AV1 decode) or MPV playback

## Installation

Download the latest APK from the [GitHub releases page](https://github.com/maguid28/Wholphin_fork/releases/latest) and sideload it to your Android TV device.

1. Enable installation from unknown sources on your device.
2. Download the APK matching your device (`arm64-v8a` for most Android TV boxes, including NVIDIA Shield).
3. Install with a file manager, Downloader app, or ADB.

The app can check for updates in Settings → Advanced when installed from GitHub.

**Application ID:** `com.ndorfin.app`

## Compatibility

- Android 6+ / Fire OS 6+
- Jellyfin server 10.10.x or 10.11.x (primarily tested on 10.11)

## Building

See [DEVELOPMENT.md](DEVELOPMENT.md).

## Contributing

Issues and pull requests are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md).

## Acknowledgements

- [Jellyfin](https://jellyfin.org/) media server and SDK
- [Wholphin](https://github.com/damontecres/Wholphin) — upstream project this fork is based on
- All open-source libraries used in this project

## License

Same license as the upstream Wholphin project. See repository license file for details.
