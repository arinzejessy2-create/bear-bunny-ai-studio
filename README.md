# JESSY 8.0 — Deep Search

JESSY is a dark, mobile-first search interface backed by a self-hosted SearXNG instance. It searches public web sources only.

## Run
```sh
docker compose up --build
```
Open http://localhost:3000. The Node service expects SearXNG at `http://searxng:8080`.

## Modes
Deep Search fans out across general web, videos, science, news and files, then deduplicates URLs. Individual modes query the corresponding SearXNG category.

## Android
Open `android/` in Android Studio and build the debug APK. Set Gradle property `JESSY_API_BASE_URL` to the deployed HTTPS backend URL before building. GitHub Actions can build the APK and upload it as an artifact.

## Proxy limits
The Android WebView can use a user-supplied HTTP proxy host and port. No proxy servers, credentials, free country exit nodes, or VPN are bundled. A country selector alone cannot change IP location. The app checks its public egress IP before reporting a verified connection. This affects only JESSY's WebView, not the whole phone. SOCKS5 and authenticated proxies are not supported by this wrapper.

## Status
The repository contains source code and CI build workflow. A successful CI run and real-device test are required before calling an APK verified.
