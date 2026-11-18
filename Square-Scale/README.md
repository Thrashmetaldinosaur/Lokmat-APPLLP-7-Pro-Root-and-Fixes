# Square Scale

Vector (LSPosed) module for the Lokmat APPLLP 7 Pro that makes the built in square app mode bigger.

## The problem

The watch can shrink an app into a square so portrait or square UIs fit the round 466x466 screen. When you turn square on for an app, the system writes a fixed preset of **0.72**, which gives a small ~336 px square with a lot of wasted screen.

Apps that are unresizeable and portrait locked (Unity games like Reigns: Her Majesty) ignore `wm size` and `wm density` overrides, so this scaler is the only thing that works for them.

## How the square mode works

Turning square on writes four global `system` settings (from a uid 1000 system component):

| Setting | Off | On |
| --- | --- | --- |
| `watch_app_screen_type` | 0 | 1 |
| `watch_small_screen_stat` | 0 | 1 |
| `watch_small_screen_scale_h` | 0.86 | 0.72 |
| `watch_small_screen_scale_v` | 0.86 | 0.72 |

The scale settings are global, not per app. Every time square is turned on, the 0.72 preset is written again, so setting the value once with `settings put` does not stick.

## The fix

The module hooks the settings provider inside `system_server`. When something writes `0.72` to `watch_small_screen_scale_h` or `watch_small_screen_scale_v`, it swaps the value for **0.85** before it is saved. No background script, no polling, no delay.

Only the exact 0.72 preset is changed. Any other value passes through untouched.

## Install

1. Install the APK: `adb install -r SquareScale.apk`
2. Open Vector (or LSPosed), go to Modules, enable **Square Scale**.
3. Scope: tick **System Framework** (`android`).
4. Reboot the watch.
5. Turn square on for any app from the watch menu.

Check it is working:

```
adb shell settings get system watch_small_screen_scale_h
```

Should print `0.85` while square is on.

## Change the size

The value comes from a system property, default 0.85 (allowed range 0.5 to 1.0). No rebuild needed:

```
adb shell su -c "setprop persist.sys.square_scale 0.88"
```

It applies the next time square is turned on. 0.85 is about a 396 px square, 0.88 is about 410 px.

## Remove

Disable the module in Vector and reboot, or:

```
adb uninstall com.zvi.squarescale
```

## Requirements

* Root (Magisk) with Zygisk
* Vector or LSPosed
* Tested on the APPLLP 7 Pro, Android 15

## Source

`Hook.java` and `AndroidManifest.xml` are the full source. Built against the Xposed API (min version 93), minSdk 30.

## Disclaimer

This hooks `system_server`. A broken hook there can stop the watch from booting until the module is disabled (Vector safe mode or disabling Zygisk modules from recovery). Use at your own risk.
