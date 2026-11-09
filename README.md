[简体中文](README.zh-CN.md) / [English](README.md)

# Safer Lockscreen

Safer Lockscreen is a small, privacy-first Android app for Google Pixel phones. While the device is locked, it blocks the Quick Settings / notification-shade pull-down, and it can also close the power menu.

Stock Pixel Android does not offer a switch to disable that pull-down. Anyone holding a locked phone can swipe down and toggle airplane mode, Wi-Fi, or mobile data. Android 17’s **Lock Quick Settings connectivity** setting only freezes a few connectivity tiles. The shade itself still opens, and the other tiles still work. This app is aimed at that remaining gap.

Package name: `com.whstudio.saferlockscreen`.

This project is not affiliated with LSDroid or Cerberus Lock Screen Protector. It does not contain code from that app. It uses public Android APIs only.

## What it does

Protection runs only while `KeyguardManager.isDeviceLocked()` is true. That means Android still requires a PIN, pattern, or password. After you unlock, Quick Settings and the power menu work normally. Smart Lock and Extend Unlock count as unlocked, so the app stays out of the way and does not fight your own Quick Settings.

While the phone is locked:

- **Block Quick Settings on the lock screen** (on by default once you turn protection on) closes the notification shade and Quick Settings.
- **Block power menu on the lock screen** (on by default) closes the power-off / restart menu.
- The lock-screen **Emergency** button is not blocked. Back is not pressed while the emergency dialer or Personal Safety SOS screen is open, so a call is not torn down. That also means the emergency path can sometimes still reach Quick Settings.
- The clock, PIN pad, pattern, fingerprint target, and the rest of the lock screen stay uncovered.
- The app does not turn the screen off.

Protection has one behavior. A transparent accessibility overlay covers the top edge so the shade swipe does not start. If a Quick Settings or notification panel is actually expanded across most of the screen, the app dismisses it. A separate switch closes the power menu the same way, without blanking the display.

### Why it is built this way

There is no public app API that disables the lock-screen shade. Hidden status-bar APIs are signature-only, so this app does not use them.

AOSP keeps a System UI window titled `NotificationShade` on screen for the whole time the keyguard is up, including while the shade is collapsed. Lock-screen notifications live in that same window. Seeing the window, or seeing the notification list, is therefore not treated as “Quick Settings is open.”

The service reacts to window events and, while the screen is on and locked, re-checks a few times a second. It classifies a surface only from system-window identity:

- window title and pane title
- class name
- view ids such as the Quick Settings frame
- the short title carried by a window-state event

It does not read notification text, message bodies, or passwords, and it never calls the screenshot APIs.

The top-edge overlay is an accessibility overlay (`TYPE_ACCESSIBILITY_OVERLAY`) only as tall as the status-bar swipe region, including the display cutout. Touches that start there are consumed. Touches elsewhere pass through. It is removed when the screen is off, when the device unlocks, or when protection is off.

The shade window is above normal app windows. An accessibility overlay is the supported way for an accessibility service to sit above it. If a swipe still opens the panel, the app dismisses it only when that panel covers most of the screen. Seeing the lock screen, the keyguard, a collapsed `NotificationShade` window, or `ShadeScene` does not turn the display off.

Protection does not use device admin and does not call `DevicePolicyManager.lockNow()`. If device admin is still on, Android will not uninstall the app until you deactivate it. There is no wipe and no password reset.

The accessibility service, device-admin receiver, boot receiver, and application are `directBootAware`. Settings are stored in device-protected storage. After a reboot, before the first unlock, an enabled service can still guard the lock screen. The manifest receiver listens for `LOCKED_BOOT_COMPLETED`, `BOOT_COMPLETED`, and `USER_UNLOCKED`.

There is no foreground service and no persistent notification. The system keeps an enabled accessibility service running.

## Permissions

| Permission or capability | Why | How to turn it off |
| --- | --- | --- |
| Accessibility service | See when the shade, Quick Settings, or power menu is showing, dismiss an expanded panel, and cover the top-edge swipe. Android warns that a service with window access can observe the screen. This one uses that access only to identify those system panels. | Settings → Accessibility → Safer Lockscreen → Off |
| Device admin, if it is still on | Not used for protection. An active device admin blocks uninstall until you deactivate it. | In the app, **Deactivate device admin**, or Settings → Security → Device admin apps |
| `RECEIVE_BOOT_COMPLETED` | Hear boot and the first unlock so protection is re-checked. Granted at install. No prompt. | Uninstall the app |
| Internet | **Not requested.** | — |

No ads, analytics, crash reporters, Firebase, or other trackers. No screenshot APIs. No content provider and no broadcasts of screen contents to other apps. Backup and device-to-device transfer of app data are disabled.

Android will not uninstall an app that is still an active device admin. Deactivate device admin first, then uninstall.

Sideloaded apps on Android 13 and newer often cannot flip accessibility until restricted settings are allowed:

1. Settings → Apps → Safer Lockscreen
2. Open the menu (three dots) → **Allow restricted settings**
3. Return to the app and open accessibility settings
4. Turn **Safer Lockscreen** on

The in-app screen repeats this.

## Set up on the phone

1. Install the debug APK (see below).
2. Open **Safer Lockscreen**. The first time the main screen opens, read the warning. The confirm button stays disabled for 5 seconds, then you tap it. The dialog does not close by itself, and it does not close if you tap outside it or press Back. It does not appear again unless you clear the app’s data or reinstall.
3. Enable the **accessibility service**. Read its description. It matches the paragraph above.
4. Turn on **Block lock-screen panels**. Both feature toggles can stay on.
5. If the screen says device admin is still on, deactivate it.
6. Set a PIN, pattern, or password if you have not. A swipe-only lock screen is not a lock.
7. Lock the phone and try to swipe down from the top. Quick Settings should not stay open. The screen should stay on so you can enter the PIN. Use the lock-screen Emergency button to confirm it still opens.

## Build and install

Requirements:

- JDK 17 or newer (the project compiles to Java 17)
- Android SDK Platform 37 (Android 17) and Build-Tools 37.0.0
- `ANDROID_HOME` or `local.properties` (`sdk.dir=...`) pointing at the SDK

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install on a device with USB debugging:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`minSdk` is 31 (Android 12). `targetSdk` and `compileSdk` are 37 (Android 17). The Gradle wrapper is included. There is no signing key in this repository. The debug APK is signed with the local debug keystore Gradle creates.

GitHub Actions builds the debug APK and runs the unit tests on push and pull request. The workflow installs SDK platform `android-37.0` itself.

## If you cannot use the phone

This app has not been fully tested on every Pixel build. There is a small chance it could prevent you from unlocking normally. The overlay can get in the way of the lock-screen power menu. Do not count on long-pressing Power on the lock screen to recover.

1. If USB debugging was already authorized, connect the phone to a computer and run:

   ```bash
   adb uninstall com.whstudio.saferlockscreen
   ```

2. Otherwise, force a restart by holding the Power button for about 30 seconds, until the phone restarts. Do not use the lock-screen power menu for this.

3. When the Google G logo appears, hold Volume Down until the phone finishes starting. That opens Safe Mode.

4. In Safe Mode, deactivate device admin if it is still on (in the app, or in Settings → Security → Device admin apps). Then uninstall Safer Lockscreen. Restart the phone to leave Safe Mode.

## Known limitations

- The shade or the power menu can flash briefly before it is dismissed. The overlay is what blocks the common swipe. It cannot promise a zero-frame race against System UI.
- Android 16+ System UI (scene container / Flexiglass) keeps the shade window on screen while the lock screen is showing, including when the shade is collapsed. The app blocks the swipe with the top-edge overlay and only dismisses a panel that covers most of the screen. It does not turn the display off.
- Behavior on non-Pixel skins can differ. The classifier knows a few common titles (“Control center”, and the Simplified Chinese system strings), but this app is aimed at stock Pixel Android 17.
- While the emergency dialer is open, Back is not pressed. Someone who opens Emergency first may still reach some tiles if dismiss does not win.
- The power button’s path into Emergency goes through the power menu. Blocking that menu can interrupt that shortcut, and the top-edge overlay can get in the way of the power menu. The Emergency control drawn on the lock screen is separate and is not covered. Do not rely on long-pressing Power on the lock screen to recover.
- This does not stop someone who knows the PIN, and it does not help if the phone has no screen lock. It is not a device-tracking or remote-wipe tool. Those features are intentionally absent.
- Another accessibility service can conflict with the overlay. TalkBack users should try the combination before relying on it.
- Always-on display is left alone: the overlay is removed while the screen is not interactive, so it does not cover the AOD or eat the whole tap-to-wake area. The top edge can still wake the phone.

## Privacy

The app has no network permission and no code that opens a socket. Preferences never leave the phone. Logs record only the action that ran (`dismissShade`, `dismissPowerMenu`) and exception class names. They do not record window titles or notification text.

## License

[Apache License 2.0](LICENSE). Copyright 2026 WHStudio.
