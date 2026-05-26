# android

Kotlin + Compose app. Captures the driver app's screen via `MediaProjection`, runs
ML Kit OCR on the offer card, POSTs to the backend, renders a heads-up verdict
notification.

## Prereqs

- Android Studio (Hedgehog or newer recommended — Iguana / Koala both work).
  Snap install on Ubuntu: `sudo snap install android-studio --classic`.
- Android SDK 35 (installed via Studio's SDK Manager on first project open).
- Physical device on Android 8+ with USB debugging.
  Emulator works for compile but cannot screen-capture the real Uber/Cabify apps.

## First open

1. Android Studio → "Open" → select the `android/` directory.
2. Studio will sync Gradle. It generates `gradlew` and downloads Gradle 8.10+
   automatically on first sync.
3. SDK 35 will be flagged as missing — accept the prompt to install.

## Configure backend URL + secret

Edit `android/local.properties` (or pass `-PBACKEND_URL=...`):

```
BACKEND_URL=https://your-cf-tunnel-hostname.tld
BACKEND_SECRET=change-me-please
```

These are wired in via `buildConfigField` in `app/build.gradle.kts` and exposed
to code as `BuildConfig.BACKEND_URL` / `BuildConfig.BACKEND_SECRET`.

## Permissions to grant on first run

The MainActivity walks you through them in order:

1. **Notifications** — POST_NOTIFICATIONS (Android 13+).
2. **Location** — fine location, used for the driver's current lat/lng.
3. **Usage access** — special permission; opens system Settings.
   Required so `ForegroundDetector` can confirm Uber/Cabify is in the foreground
   before processing OCR frames.
4. **MediaProjection** — system consent dialog. Must be re-granted after every
   reboot (Android 14+ also after every process death).

## Files

- `MainActivity.kt` — Compose UI with permission buttons + start/stop capture
- `CaptureService.kt` — foreground service, `ImageReader` @ 2 fps
- `OfferParser.kt` — platform detection + price regex (pickup/dropoff TODO)
- `BackendClient.kt` — POST `/evaluate`
- `Notifier.kt` — heads-up verdict notification
- `ForegroundDetector.kt` — `UsageStatsManager` lookup for driver app pkg
