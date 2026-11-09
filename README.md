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
- The app does not turn the screen off. There is no force-lock mode.

Protection has one behavior. A transparent accessibility overlay covers the top edge so the shade swipe does not start. If a Quick Settings or notification panel is actually expanded across most of the screen, the app dismisses it. A separate switch closes the power menu the same way, without blanking the display. Older “Dismiss shade” and “Force lock” choices are ignored.

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

This version does not use device admin and does not call `DevicePolicyManager.lockNow()`. The device-admin component remains only so an administrator enabled by an older build can be deactivated. Android will not uninstall an app while that administrator is still active. There is no wipe and no password reset.

The accessibility service, device-admin receiver, boot receiver, and application are `directBootAware`. Settings are stored in device-protected storage. After a reboot, before the first unlock, an enabled service can still guard the lock screen. The manifest receiver listens for `LOCKED_BOOT_COMPLETED`, `BOOT_COMPLETED`, and `USER_UNLOCKED`.

There is no foreground service and no persistent notification. The system keeps an enabled accessibility service running.

## Permissions

| Permission or capability | Why | How to turn it off |
| --- | --- | --- |
| Accessibility service | See when the shade, Quick Settings, or power menu is showing, dismiss an expanded panel, and cover the top-edge swipe. Android warns that a service with window access can observe the screen. This one uses that access only to identify those system panels. | Settings → Accessibility → Safer Lockscreen → Off |
| Device admin, only if an older build turned it on | Not used for protection. It can still block uninstall until you deactivate it. | In the app, **Deactivate device admin**, or Settings → Security → Device admin apps |
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
2. Open **Safer Lockscreen**. Each time the main screen opens, read the warning. The confirm button stays disabled for 5 seconds, then you tap it. The dialog does not close by itself, and it does not close if you tap outside it or press Back.
3. Enable the **accessibility service**. Read its description. It matches the paragraph above.
4. Turn on **Block lock-screen panels**. Both feature toggles can stay on.
5. If the screen says an older device admin is still on, deactivate it.
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

## If the phone turns the screen off, or you cannot get past the lock screen

Older builds could treat the lock screen itself as open Quick Settings and call force-lock. The phone then woke and immediately turned the screen off again, so the PIN pad never stayed up. A force-lock build could also flicker the display off in Safe Mode. This build does not call `lockNow()`. The overlay can still interfere with the lock-screen power menu, so do not rely on long-pressing Power there.

Recover without opening the app:

1. If USB debugging was already authorized, connect the phone and run:

   ```bash
   adb uninstall com.whstudio.saferlockscreen
   ```

2. Otherwise, force a restart by holding the Power button for about 30 seconds, until the phone restarts. Do not depend on the lock-screen power menu.

3. When the Google G logo appears, hold Volume Down until startup finishes. That opens Safe Mode. Third-party apps, including this one, do not run their protection there.

4. In Safe Mode, deactivate device admin if an older build left it enabled (Settings → Security → Device admin apps). Then uninstall Safer Lockscreen. Restart to leave Safe Mode.

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

## 中文说明

**更安全锁屏**是一个面向 Google Pixel 的小型、隐私优先的 Android 应用。手机锁定时，它阻止下拉快捷设置和通知栏，也可以关闭电源菜单。

Pixel 原版系统不能关闭锁屏上的通知栏下拉。Android 17 的“锁定快捷设置连接”只会冻住 WLAN、蓝牙、移动数据和飞行模式等少数开关，通知栏本身仍能拉开。本应用拦截的是通知栏下拉。

包名：`com.whstudio.saferlockscreen`。本项目与 LSDroid / Cerberus 无关，也不包含其代码。

### 行为

只在 `KeyguardManager.isDeviceLocked()` 为真时工作，也就是系统仍要求 PIN、图案或密码。解锁之后，快捷设置和电源菜单恢复正常。智能解锁和延长解锁视为已解锁，应用不会干预。

- **在锁屏上阻止快捷设置**：关闭通知栏和快捷设置。
- **在锁屏上阻止电源菜单**：关闭关机 / 重新启动菜单。
- 锁屏上的**紧急呼叫**按钮不会被挡住。紧急拨号或个人安全 SOS 界面打开时，不会按返回键，避免掐断通话。因此这条路径有时仍可能碰到快捷设置。
- 应用不会关闭屏幕。没有强制锁屏。

防护只有一种行为：用无障碍悬浮层盖住顶部下拉区域；只有快捷设置或通知栏真正盖住屏幕大部分时才收起它。电源菜单用另一个开关以同样的方式关闭，不会熄屏。旧版本里选择的“收起通知栏”或“强制锁屏”会被忽略。

无障碍服务、开机广播和应用本身都标记了 `directBootAware`，设置放在设备受保护存储中，因此重启后、首次解锁前也能防护。没有常驻通知。这个版本不使用设备管理器，也不调用 `lockNow()`。设备管理器组件只为了让旧版本已经启用的管理员可以被停用。管理员仍启用时，Android 不允许卸载。不能清除数据，也不能重置密码。

无障碍服务只根据窗口标题、窗格标题、类名和视图 id 识别系统面板。不读取通知正文、短信或密码，也不截屏。

### 权限与关闭方式

- **无障碍服务**：识别并关闭真正打开的锁屏通知栏、快捷设置和电源菜单，并挡住顶部下拉。关闭：设置 → 无障碍 → 更安全锁屏 → 关闭。
- **设备管理器**：防护不再使用它。如果旧版本启用过，卸载前必须停用。关闭：应用内“停用设备管理器”，或设置 → 安全 → 设备管理应用。
- **开机完成广播**：重启和首次解锁时复查防护。安装时授予，没有弹窗。
- **没有网络权限。** 没有广告、统计或崩溃上报。数据不离开手机，也不提供给其他应用。已关闭备份。

卸载前必须先停用设备管理器，否则系统会拒绝卸载。

侧载应用在 Android 13 及更新版本上，常常要先允许受限制的设置，无障碍开关才可点：

1. 设置 → 应用 → 更安全锁屏
2. 右上角菜单 → **允许受限制的设置**
3. 回到应用，打开无障碍设置并启用“更安全锁屏”

### 编译与安装

需要 JDK 17+、Android SDK Platform 37 和 Build-Tools 37.0.0。

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`minSdk` 为 31，`targetSdk` / `compileSdk` 为 37。仓库里没有签名密钥。调试 APK 使用本机的 debug 签名。

每次打开主界面，都会先显示一段说明。确认按钮会禁用 5 秒并显示倒计时，到时后需要你点一下才会继续。点外面或按返回键都不会关掉它，时间到了也不会自己消失。

### 如果屏幕被关掉，或无法通过锁屏

旧版本可能把锁屏本身当成已经打开的快捷设置并强制锁屏。手机会亮一下马上再熄灭，PIN 键盘停不住。强制锁屏的版本甚至可能让安全模式的屏幕闪一下就关掉。这个版本不会调用 `lockNow()`。顶部遮罩仍可能妨碍锁屏上的电源菜单，不要指望在锁屏上长按电源键来恢复。

不打开应用的恢复办法：

1. 如果以前已经授权过 USB 调试，连接电脑后执行 `adb uninstall com.whstudio.saferlockscreen`。
2. 否则按住电源键大约 30 秒，直到手机重新启动。不要依赖锁屏上的电源菜单。
3. 出现 Google 的 G 标志时，按住音量下键，直到启动完成。这会进入安全模式。第三方应用的防护不会在那里运行。
4. 在安全模式中，如果旧版本留下了设备管理器，请先在“设置 → 安全 → 设备管理应用”里停用，然后卸载“更安全锁屏”。重启即可退出安全模式。

### 限制

通知栏或电源菜单可能在被关掉前短暂闪现。Android 16 及更新版本的 System UI 在锁屏显示时，通知栏窗口一直都在，包括收起的时候。“自动”用顶部边缘挡住下拉，只有面板盖住屏幕大部分时才会收起它，并且不会因此强制锁屏。这不能代替一个可靠的屏幕锁定。紧急呼叫路径、电源键上的紧急入口、生物识别被跳过一次，以及不同厂商的差异，见上面的英文说明。

## License

[Apache License 2.0](LICENSE). Copyright 2026 WHStudio.
