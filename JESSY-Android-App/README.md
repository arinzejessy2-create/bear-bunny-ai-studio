# JESSY Android app

Native Android application project (Kotlin + Android WebView), not a website shortcut.

- Installs as an Android app named JESSY.
- Has its own search/address bar and in-app browser.
- Search results load inside JESSY using DuckDuckGo HTML search; it does not launch the phone's Google app.
- Includes back, reload, home, basic history/cache clearing, and file downloads.

Build with JDK 17 and Android SDK 35 using Gradle 8.9: `gradle assembleDebug`.
APK output: `app/build/outputs/apk/debug/app-debug.apk`.

This is an installable browser shell using DuckDuckGo for search, not a self-hosted independent crawler/index.