# JESSY 🫟🫟 — Android private-search browser

JESSY is a native Android WebView browser with a dark search dashboard, a curated free/open resource directory, per-app HTTP proxy controls, private browsing cleanup, downloads, and support for opening Tor onion URLs when Android traffic is routed through Orbot.

## Features in this build

- DuckDuckGo HTML search, with results kept inside JESSY.
- Curated links to legitimate free/open resources: Project Gutenberg, OpenStax, F-Droid, arXiv, Internet Archive, Wikibooks, GitHub Explore, Khan Academy and Wikimedia Commons.
- HTTP proxy override for JESSY WebView using AndroidX WebKit ProxyController. The user supplies a trusted proxy host and port and may label it with a country. The label is not a geolocation guarantee; the actual exit country depends on the proxy operator.
- Tor handoff: open Orbot, connect Tor, and enable JESSY in Orbot's per-app routing list. Then enter a .onion address in JESSY. Orbot is a separate app and must be installed and connected; JESSY does not falsely claim to bundle Tor.
- Private-mode toggle clears WebView cookies, cache, history and form data when enabled. It is local cleanup, not a guarantee of anonymity.
- File downloads through Android DownloadManager.

## Important limits and safety

- This build does not bundle a free public proxy pool. Public proxies are frequently unreliable and may monitor or modify traffic. Use a proxy operator you trust.
- The proxy feature controls WebView requests only, not all traffic from the phone. It requires an Android System WebView version supporting AndroidX WebKit PROXY_OVERRIDE.
- Tor onion browsing requires Orbot to route JESSY's traffic. A proxy and Tor are separate connection modes.
- A proxy country label is user-entered metadata, not proof of the server's actual location.
- JESSY is not a dark-web index and does not provide illegal material. Onion URLs must be supplied by the user or obtained from legitimate sources.
- Private mode does not hide activity from websites, proxy operators, network providers, or downloaded files.

## Build

Use JDK 17, Android SDK platform 35, and Gradle 8.9:

```sh
gradle --no-daemon assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.
