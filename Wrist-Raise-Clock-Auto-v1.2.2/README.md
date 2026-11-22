# Automatic wrist-raise clock — stable v1.2.2

**Current tested release:** LOKMAT APPLLP 7 Pro (`WP_C30_VP69_OLED`), Android 15, firmware `LOKMAT_APPLLP_7_PRO_C30_EN_V1.0_20260921`, Magisk 31.1. Earlier [v1.1](../Wrist-Raise-Clock-Auto-v1.1/README.md) is preserved. This is an Android application, **not a Vector/LSPosed module**.

## Download and install

[Download the tested v1.2.2 APK](Glance-Auto-v1.2.2.apk). SHA-256: `BB88AFC137213F2A58559C19F0DCDA444D189153172D07F4BCAA13BA4F7A684D`.

**Important signature warning:** This is the **exact APK extracted from the tested watch**. It uses the original local signing certificate. If you're upgrading an existing installation, your existing APK must use that same signing certificate, or Android will reject `adb install -r`. **Do not uninstall a working app merely to force an upgrade**; uninstalling removes preferences and may require renewed Magisk authorization. A locally built debug APK will typically use a different certificate.

```sh
adb -s WATCH_IP:PORT shell getprop ro.product.device
adb -s WATCH_IP:PORT shell getprop ro.build.display.id
adb -s WATCH_IP:PORT install -r Glance-Auto-v1.2.2.apk
adb -s WATCH_IP:PORT shell appops set com.appllp7.glanceauto SYSTEM_ALERT_WINDOW allow
adb -s WATCH_IP:PORT shell am start -n com.appllp7.glanceauto/.GlanceActivity
```

Verify the exact firmware before installing. Ensure superuser access to Glance Auto in Magisk. Start the app, press **START AUTO**, and optionally turn **REBOOT START: ON** on if you want the foreground service restored at next boot. The first normal screen-on after boot attaches the overlay; the next sleep/wake cycle resumes gesture clocks. You may need one power-button screen-off following a reboot before automatic rearming.

## Verified features

- Raise wrist: large black clock showing time, day and date. Physical power button wakes without displaying the clock.
- Keeps a single transparent touch-through overlay attached while awake and promotes it to an opaque shield when asleep; **prearms capped 20% brightness during screen-off**, preventing the initial bright flash.
- Clock times out after approximately 8.5 seconds, requests sleep, and rearms automatically. Tap dismisses the clock immediately.
- Paused-video/media guard prevents unintended paused-video playback when the clock is shown, while retaining legitimate ongoing audio.
- Reboot startup is user-opt-in; **STOP / DISARM** also disables it.
- Root logcat is used to distinguish wake causes; there is **no dependency on Vector or the experimental direct-event bridge**.
- The on-watch six-wakeup test passed (three wrist raises, three power-button wakes). Systematically measured battery gains have not been established.

## Notification fix

The app requires a running foreground service and Android overlay permission, so two notification cards may appear. The independent [notification fix](../Hide-Glance-Notifications/README.md) hides the per-app Android System overlay-warning channel by setting its importance to **None**, and optionally snoozes the app's own foreground-service notification for one year. Neither operation requires rebuilding the app; neither changes the clock. The foreground notification may return after a service restart or when the snooze expires.

## Build from source

Requirements: JDK 17, Gradle 8.9, Android SDK platform 35, Android Gradle Plugin 8.7.3, Windows PowerShell 7 for the optional offline tests.

```sh
gradle --no-daemon --offline :app:assembleDebug
```

The generated debug APK is separately signed with **your** local debug key and is not necessarily an in-place upgrade for the published tested APK.

Source: `app/src/main/java/com/appllp7/glanceauto/`. Offline policy and timing regression suites: `qa/`. No hardware firmware images or watch data are included.

## Emergency stop

```sh
adb -s WATCH_IP:PORT shell am force-stop com.appllp7.glanceauto
```

Normal STOP / DISARM disables reboot start. Force-stop can prevent boot receivers from launching the app until it is opened manually. This does not alter Magisk, firmware, the OEM wrist-raise setting, or the separate first-stage Glance beta.

**Firmware scope:** Other LOKMAT models and firmware revisions have not been tested. **Privacy:** This release contains public source, a signed APK and synthetic tests only; no private watch logs, personal file paths, unique devices or keystores.
