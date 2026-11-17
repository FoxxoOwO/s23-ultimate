# S23 Ultra Enhancements (LSPosed)

LSPosed module + companion app styled with Samsung One UI design for the **Galaxy S23 Ultra** (SM-S918B, One UI 9 / Android 16–17).

The module unlocks features that Samsung has software-locked on the S23. Each feature can be individually toggled on or off inside the companion app — without requiring a device reboot.

> ⚠️ **Do NOT reboot the device** if you are using tethered root (KernelSU / volatile jailbreak) — rebooting will result in losing root access. After reinstalling or updating the module, a simple `force-stop` of the target application is sufficient.

## Features

| # | Feature | Preference Key |
| :-- | :--- | :--- |
| 1 | Log Video in Pro Video mode | `pref_camera_log_video` |
| 2 | 4K60 lens switching across all cameras | `pref_camera_4k60_lenses` |
| 3 | 100% JPEG capture quality | `pref_camera_jpeg_100` |
| 4 | Shutter sound mute toggle | `pref_camera_shutter_mute` |
| 5 | Outdoor Mode (unlocks toggle in Settings) | `pref_display_outdoor_mode` |
| 6 | Display color profiles (AMOLED Cinema / Photo / Basic) | `pref_display_screen_mode` |
| 7 | Extended screen timeouts (30 min, 1 hr, Never) | `pref_display_screen_timeout` |
| 8 | Action timeout customization | `pref_display_action_timeout` |
| 9 | Adaptive Color Tone (EAD) | `pref_display_adaptive_color_tone` |
| 10 | Font restriction unlock | `pref_display_unlock_fonts` |
| 11 | Native call recording | `pref_telephony_call_recording` |
| 12 | Show saved Wi-Fi passwords | `pref_system_show_wifi_passwords` |
| 13 | Bypass screenshot restriction (FLAG_SECURE) | `pref_system_disable_flag_secure` |
| 14 | Disable telemetry and error reporting | `pref_system_disable_telemetry` |
| 15 | Detailed battery information | `pref_system_battery_info` |
| 16 | 4K120 in Slow Motion mode | `pref_camera_4k120_slowmo` |

For an in-depth breakdown of each hook, discovered boundaries, and **features that cannot be ported** (Quick Share to iPhone, Now brief/nudge, corner gesture), refer to [NOTES.md](NOTES.md).

## Architecture

```
App (One UI)  ──►  SharedPreferences  ──►  mirror to Settings.Global (s23enh_<key>)
                                                     │
                                                     ▼
                       HookEntry (LSPosed) ── reads via getSystemContext()
                       ├── system_server      (FLAG_SECURE, telemetry)
                       ├── Camera             (Log, 4K60, 4K120, JPEG, shutter)
                       ├── Settings           (display, timeouts, fonts, battery, Wi-Fi passwords)
                       ├── SystemUI           (outdoor mode, swipe-up)
                       └── Phone/InCallUI     (call recording)
```

**Why mirror through `Settings.Global`:** On One UI 9, `getRemotePreferences()` returns an empty map inside hooked processes, and `ActivityThread.currentApplication()` is still `null` during `onPackageReady`.

## Building

Prerequisites:
- Android SDK (Build-Tools 36, platform android-36), with `ANDROID_HOME` configured
- JDK 17+
- 7-Zip (`C:\Program Files\7-Zip\7z.exe`)
- ADB (optional, for automated device deployment)
- `libxposed-api-102.jar` — download from [LibXposed API releases](https://github.com/libxposed/api/releases) and place it in the repository root

```powershell
# Build + install to a connected device
powershell -ExecutionPolicy Bypass -File .\build_and_install.ps1

# Build only (skip install)
powershell -ExecutionPolicy Bypass -File .\build_and_install.ps1 -NoInstall
```

Build pipeline: `aapt2 compile/link` → `javac --release 17` → `d8 --min-api 31` → 7-Zip → `zipalign` → `apksigner` → `adb install`.

The APK is signed with `debug.keystore` (password: `android`, alias: `debug`). For production or custom builds, generate your own keystore and update the paths in the script.

## Installation

1. Install the APK.
2. Grant required permissions:
   ```sh
   adb shell pm grant com.s23.enhancements android.permission.WRITE_SECURE_SETTINGS
   adb shell appops set com.s23.enhancements WRITE_SETTINGS allow
   ```
3. Enable the module in LSPosed and configure its scope:
   `system`, `android`, `com.sec.android.app.camera`, `com.android.settings`, `com.android.systemui`, `com.samsung.android.incallui`, `com.samsung.android.dialer`, `com.samsung.android.app.telephonyui`, `com.android.phone`.
4. After toggling any feature in the app, simply `force-stop` the target application (e.g. `adb shell am force-stop com.sec.android.app.camera`).

## License

Unlicensed — personal project. If you wish to use parts of the code, feel free to open an issue.
