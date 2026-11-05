# HEIGHT JOURNEY

Android home-routine and wellbeing tracker with a black / electric-blue interface.

## Features
- 10:00 AM workout and 10:00 PM sleep reminders (Android notifications)
- Guided mobility and posture routine with timer
- Height measurement history saved locally
- Sleep duration tracker
- Food checklist and water counter
- Evidence-based growth information and a clear note that adult-height predictions are uncertain

## Build
GitHub Actions builds a debug APK on pushes to `android/**` or manual workflow dispatch. Open Actions → Build HEIGHT JOURNEY APK → latest successful run → Artifacts → HEIGHT-JOURNEY-debug-apk.

The debug APK is installable for testing. A signed release build requires a private signing key and should be configured separately.
