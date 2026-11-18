# Glance Auto Beta v1.1 — LOKMAT APPLLP 7 Pro

**Status: User-confirmed working** on a rooted LOKMAT APPLLP 7 Pro running Android 15 (`WP_C30_VP69_OLED`, firmware `LOKMAT_APPLLP_7_PRO_C30_EN_V1.0_20260921`) with Magisk 31.1. This source is a standalone Android app, **not a Magisk module** and not a firmware image.

## What it does

- Displays a large black time/day/date glance clock on a *verified wrist-raise wake*.
- Tap the clock to dismiss it and return to the underlying app. No tap requests screen sleep after 8.5 seconds.
- Power-button wakes bypass the clock.
- Automatically rearms across subsequent sleep/wake cycles without tapping an ARM button.
- Optional **REBOOT START** control resumes operation after reboot. On the tested watch, **one initial power-button screen-off was needed** before normal repeated gesture operation; do not claim completely hands-free first-wake behavior.
- **STOP / DISARM** via the app or foreground notification disables reboot startup and stops the service.

The design uses **one continuously attached application overlay**. While normally awake it is fully transparent and not touchable; at `SCREEN_OFF` it changes the *existing* window to an opaque touch shield without creating another window while the watch sleeps. On wake it checks the cause using root-readable Android logs, shows the clock only for a recent genuine wrist gesture, and otherwise restores the transparent touch-through overlay. Unknown wake reasons fail open to ordinary use.

## Prerequisites

- Exact tested model/build listed above, bootloader unlocked and Magisk root operational. Other models or OTAs **not tested**.
- Android ADB and **wireless debugging** enabled. Run tests unplugged: USB charging can affect sleep/wake experiments.
- Gradle **8.9**, JDK **17**, Android SDK platform **35**, and Android Gradle Plugin **8.7.3** (resolved by Gradle).
- A user-approved Magisk Superuser grant for **Glance Auto Beta**. Magisk 31.1 may require manual preauthorization in **Magisk → Superuser → +** instead of an automatic popup.
- Draw-over-other-apps permission for the app (`SYSTEM_ALERT_WINDOW`).

## Build and install

From this folder, with JDK, Gradle and Android SDK available:

```sh
gradle --no-daemon :app:assembleDebug
```

The output is `app/build/outputs/apk/debug/app-debug.apk`. For a fresh install using an authorized **wireless** ADB serial (`WATCH_IP:PORT` is an example, not an actual address):

```sh
adb connect WATCH_IP:PORT
adb -s WATCH_IP:PORT shell getprop ro.product.device
adb -s WATCH_IP:PORT shell getprop ro.build.display.id
adb -s WATCH_IP:PORT install -t app/build/outputs/apk/debug/app-debug.apk
adb -s WATCH_IP:PORT shell appops set com.appllp7.glanceauto SYSTEM_ALERT_WINDOW allow
adb -s WATCH_IP:PORT shell am start -n com.appllp7.glanceauto/.GlanceActivity
```

**Verify the exact device and firmware before installation.** Grant root for the app in Magisk's Superuser settings, open Glance Auto Beta, then press **START AUTO**. The app will not attach its overlay until the display is awake. For reboot persistence, opt in using **REBOOT START: OFF/ON**, reboot normally, wake the display, then let it sleep once using the power button before testing repeated wrist raises.

**Updating an existing installation:** Android permits `adb install -r` only when the new APK has the **same signing key**. A different PC's Gradle debug keystore, including a CI build, may not match the APK already installed. Do not uninstall a working copy just to test a newer build; uninstalling erases app preferences and may require new Magisk authorization. Preserve your working signed APK for rollback.

## Emergency stop / rollback

- **Normal:** open Glance Auto Beta and press **STOP / DISARM**, or use the STOP notification action. This also disables boot start.
- **Emergency over ADB:** `adb -s WATCH_IP:PORT shell am force-stop com.appllp7.glanceauto` (does not change Magisk/OEM settings). Force-stop can suppress future boot broadcasts until the app is manually launched.
- **Remove only this app:** `adb -s WATCH_IP:PORT uninstall com.appllp7.glanceauto`. This does not remove other mods or the original separate Stage 1 beta (`com.appllp7.glancebeta`).

## Source / testing

- Application: `app/src/main/java/com/appllp7/glanceauto/`
- Android manifest: `app/src/main/AndroidManifest.xml`
- Java state, wake reason, and boot-decision regression tests: `qa/`
- Gradle project uses Java 17 and targets Android SDK 32 with SDK 35 compile. The lower target is a compatibility measure for this OEM Android 15 environment, not a guarantee of modern Android compatibility elsewhere.
- A repository GitHub Actions build produces a **debug APK artifact** when the workflow succeeds. The APK is signed by the CI debug key and should be used for *new* installations, not as an assumed upgrade for a locally signed copy.

**Validation:** Java policy/wake/boot stress suites passed offline; the original prototype completed four undisturbed wireless sleep/wake transitions with touch-through before and after each sleep. On the test watch the combined v1.1 app was confirmed running, reboot autostart worked after one initial power-button close, and subsequent automatic clock cycles continued working. **Long-term battery impact, other firmware versions and third-party device compatibility are not established.**

**Privacy:** No personal device backups, private logs, local home directories, wireless addresses, signing keys, or authentication material are included. The build script does not patch firmware or modify OEM wrist-wake or screen-timeout settings.
