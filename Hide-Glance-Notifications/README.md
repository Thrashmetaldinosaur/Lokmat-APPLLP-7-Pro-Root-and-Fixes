# Hide overlay warnings and snooze Glance Auto's ongoing notification

**Status:** Verified on LOKMAT APPLLP 7 Pro Android 15, firmware `LOKMAT_APPLLP_7_PRO_C30_EN_V1.0_20260921`. Requires authorized **root** for the channel-level fix and ADB for the foreground-service snooze. No Vector, LSPosed, Magisk module, or firmware patch.

There are **two different notifications**:

1. **"Glance Auto Beta is displaying over other apps"** is posted by Android System (package `android`, UID 1000) on a per-overlay-package channel. Deleting/disabling the channel through the Settings toggle did not reliably persist: Android later recreated it. The verified fix instead retains the channel and sets its **importance to 0 (None)** using Android's notification service API. The clock's overlay permission remains allowed. This survives channel reposting observed in the test; persistence across a full reboot has not yet been verified.
2. **"Glance Auto Beta active"** is the app's required foreground-service notification (`com.appllp7.glanceauto`, notification ID 7432). It was hidden using Android's built-in snooze for **31,536,000,000 milliseconds (365 days)** without stopping the running service. This is **temporary**; the notification may return when the service restarts or when snooze expires. Do not claim the notification requirement is removed.

## Simple workflow (PowerShell 7 + authorized wireless ADB)

From this directory:

```powershell
# Checks the existing warning category without modifying it
./Manage-Notifications.ps1 -Serial WATCH_IP:PORT -Action Check

# Hides ONLY this app's overlay warning
./Manage-Notifications.ps1 -Serial WATCH_IP:PORT -Action HideOverlay

# Optional: snoozes ONLY the Glance Auto foreground-service notification
./Manage-Notifications.ps1 -Serial WATCH_IP:PORT -Action SnoozeGlance

# Restores original Android System overlay-warning category importance (1: Min)
./Manage-Notifications.ps1 -Serial WATCH_IP:PORT -Action RestoreOverlay
```

The helper jar is **not a persistent app**. The PowerShell script creates it locally from `OverlayChannelControl.java` using JDK 17 + Android SDK 35 build tools, pushes it to `/data/local/tmp/`, invokes it once with root, verifies the result, and then deletes the temporary device file. It does not touch other notification channels. No device identifiers are hardcoded. For other overlay apps, supply `-PackageName org.example.app`; only an *existing* Android System overlay-warning channel can be modified.

To reverse an active foreground-service snooze, try `adb shell cmd notification unsnooze 'NOTIFICATION_KEY'` with the exact key from `adb shell cmd notification list`. Some Android builds reject unsnooze under shell UID, as happened on the tested watch. Restarting the app service can cause it to repost, but doing so disrupts the working clock; **do not restart only to undo a snooze**.

**Safety:** Do not use `pm clear`, disable notification services, revoke `SYSTEM_ALERT_WINDOW`, or block all `android` notifications. Those approaches affect unrelated systems or the wrist-clock. Never upload logs, private APK signing keys, network addresses, or personal watch backups.

## Observation and limitations

- Overlay warning confirmed importance=0 and absent from active notification list after adjustment.
- Glance foreground-service notification snoozed for one year and absent from active list, with Glance Auto's foreground service confirmed still running.
- This fixes notification visibility, **not** app power draw or underlying Android foreground-service requirements.
- App-specific warning suppression and the temporary FGS snooze are independent; either can be applied separately.
