# Changelog

All notable changes to **S23 Enhancer** are documented in this file.

## [3.0] - 2026-11-28

### Added
- **Camera → Skip post-processing** (`pref_camera_skip_postproc`, [HookEntry#hookCameraPostProcessing](S23_LSPosed_Module/app/src/main/java/com/s23/enhancements/HookEntry.java)):
  - Skips Samsung Camera 17's still-picture post-processing (HDR fusion, AI ISP, super resolution, de-flicker, face restoration, beauty, selfie correction) so the saved photo keeps the raw ISP/JPEG output.
  - Hooks `com.samsung.android.camera.core2.node.NodeChain#enableNodeChain` and refuses to enable the 25 processing chains from `NodeChainKeyContainer`; a disabled chain passes its input through unchanged. Encoding, EXIF, watermark and filter chains stay enabled, and video recording is unaffected.
  - Pref is read through the app's own `ActivityThread#currentApplication` context (`isPrefAppCtx`), because `ActivityThread#getSystemContext` belongs to package `android` and every `Settings.Global` read through it throws `SecurityException: Given calling package android does not match caller's uid` in app processes.
  - Added toggle in the **Camera** section of [MainActivity](S23_LSPosed_Module/app/src/main/java/com/s23/enhancements/ui/MainActivity.java) with English and Czech (`values-cs`) strings.

## [3.2] - 2026-11-28

### Added
- **Status bar → Hide battery percentage** (`pref_sb_hide_battery_pct`, [SystemUiHooks#hookBatteryPercent](S23_LSPosed_Module/app/src/main/java/com/s23/enhancements/SystemUiHooks.java)):
  - Keeps Samsung's battery pill in the status bar but drops the number inside it. The percentage is not driven by a setting on One UI 9: `shouldShowText` in the Compose battery layout is hardcoded to `true`, so `display_battery_percentage`, `status_bar_show_battery_percent`, `battery_percent` and `show_battery_percent` in `Settings.System`/`Secure`/`Global` change nothing.
  - Hooks the Compose draw lambda `com.android.systemui.battery.view.SamsungBatteryComposeViewKt$$ExternalSyntheticLambda0#invoke` and blanks its captured text (`f$3`) before `drawText` runs, so the pill keeps its icon and level fill.
- **Status bar → Battery pill width** (`pref_sb_battery_pill_width`, `0–100 %`):
  - `0 %` (default) keeps Samsung's layout, which permanently reserves room for three digits; higher values give that space back and shrink the pill towards just the icon.
  - Applied by hooking the `com.android.systemui.battery.BatteryLayoutState` constructor and subtracting `measureText("100") × value / 100` from `totalWidthPx`.

## [3.1] - 2026-11-28

### Added
- **Camera settings → Skip post-processing switch** ([HookEntry#hookCameraSettingsToggle](S23_LSPosed_Module/app/src/main/java/com/s23/enhancements/HookEntry.java)):
  - The bypass is now controllable from inside the Camera app: the switch is injected into the Camera settings screen (`sc.p1#onCreatePreferences`) and stored in the Camera's own preferences (`s23enh_skip_postproc` in `com.sec.android.app.camera_preferences`), so it takes effect without the companion app.
  - `onResume` re-attaches the row's click listener, because the Camera settings fragment overwrites the listener of every row with its own and silently drops unknown keys.
  - The stored value is read tolerantly (Boolean or String), since Samsung's settings framework may persist it as a string.
  - The companion-app toggle keeps working as the default until the in-camera switch is used; its value wins afterwards.

## [2.9] - 2026-11-28

### Added
- **Settings → Improve fingerprint accuracy** (`pref_system_fp_improve_accuracy`, [HookEntry#hookFingerprintImproveAccuracy](S23_LSPosed_Module/app/src/main/java/com/s23/enhancements/HookEntry.java)):
  - Unlocks the Galaxy S26 / One UI 8.5–9 **Improve accuracy** option in each registered fingerprint's popup menu (*Settings → Security and privacy → Screen lock and biometrics → Fingerprints → select a fingerprint → Improve accuracy*, 10 additional scans to improve recognition with screen protectors).
  - Intercepts `FingerprintSettings#onPreferenceTreeClick` and `MenuBuilder#removeItem` to keep `R.id.update` (bypassing stock Settings' `Build.VERSION.DEVICE_INITIAL_SDK_INT < 36` check) and launches One UI's built-in `BiometricSetting` `FingerprintUpdateActivity`.
  - Added toggle in the **System** section of [MainActivity](S23_LSPosed_Module/app/src/main/java/com/s23/enhancements/ui/MainActivity.java) with full English and Czech (`values-cs`) localization.

## [2.8] - 2026-11-27

### Security
- Removed the unauthenticated `RECEIVER_EXPORTED` `ACTION_RESTART_SYSTEMUI` broadcast receiver in [SystemUiHooks](S23_LSPosed_Module/app/src/main/java/com/s23/enhancements/SystemUiHooks.java); SystemUI restart triggers strictly via the `WRITE_SECURE_SETTINGS`-protected `Settings.Global` observer.
- Enforced caller verification (`com.s23.enhancements`, `SYSTEM_UID`, `uid 0`) in [HookEntry](S23_LSPosed_Module/app/src/main/java/com/s23/enhancements/HookEntry.java)'s `ContentProvider.call` hook for `set_outdoor_mode`.
- Removed the unused exported `PrefProvider` from `AndroidManifest.xml` and removed world-readable `shared_prefs` `setReadable(true, false)` calls.
- Restricted [AppUpdater](S23_LSPosed_Module/app/src/main/java/com/s23/enhancements/AppUpdater.java) APK downloads and HTTP redirects to HTTPS GitHub domains (`isAllowedDownloadUrl`) and added `AtomicBoolean` guards against duplicate checks/downloads.
- Added optional environment-variable release signing configuration (`RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`) in `build.gradle.kts` with backward-compatible `debug.keystore` fallback.

### Performance & Bug Fixes
- Cached all [SystemUiHooks](S23_LSPosed_Module/app/src/main/java/com/s23/enhancements/SystemUiHooks.java) preferences in memory refreshed by a single `Settings.Global` `ContentObserver`, plus `WeakReference` view caches — eliminating per-frame Binder IPC and repeated `findViewById` walks inside `OnPreDrawListener`.
- Hoisted camera `CaptureRequest.Key` constants, gated optical-fallback overrides behind `pref_camera_4k60_lenses`, cached `pref_camera_jpeg_100`, short-circuited `ClassLoader#loadClass` once `ComplicationWidgetSizeManager` is hooked, deduplicated `AppInfo` preference listener/logging, and fixed `getTimeoutDescription` overload handling in `HookEntry`.
- Synced the Status Bar flashlight toggle with system torch state via `CameraManager.TorchCallback` in [StatusBarActions](S23_LSPosed_Module/app/src/main/java/com/s23/enhancements/StatusBarActions.java).
- Updated [RecentsMenuHooks](S23_LSPosed_Module/app/src/main/java/com/s23/enhancements/RecentsMenuHooks.java) cache clearing to respect `UserHandle#myUserId` and replaced synchronous `sh -c` fallback in `MainActivity` with async `settings put` execution.
- Updated `build_and_install.ps1` to fail fast on `adb install -r` errors.

### Build, Tests & Hygiene
- Enabled R8 code minification and resource shrinking ([proguard-rules.pro](S23_LSPosed_Module/app/proguard-rules.pro)), reducing release APK size from 15.5 MB to **3.23 MB** (~79% smaller).
- Removed 11 dead pre-SESL9 Java classes, 7 unused drawables, unused colors, and 2 unused strings (`batt_cycles`, `toast_error`).
- Added JVM unit tests ([ModuleLogicTest](S23_LSPosed_Module/app/src/test/java/com/s23/enhancements/ModuleLogicTest.java)) and a GitHub Actions validation workflow ([ci.yml](.github/workflows/ci.yml)), sanitized local paths/IPs in `NOTES.md`, and deduplicated `.gitignore`.

## [2.7] - 2026-11-27

### Added
- **Settings → App info package & install date** (`pref_system_app_info_details`, [HookEntry#hookAppInfoDetails](S23_LSPosed_Module/app/src/main/java/com/s23/enhancements/HookEntry.java)):
  - Injects **Package name** (tap to copy to clipboard) and **Install date** (plus **Updated** timestamp when updated more than 1 minute after initial install) directly below **App version** in `Settings > Apps > App info` (`AppVersionPreferenceController#updateState`).
- **Full Czech (`cs`) localization**:
  - Added [values-cs/strings.xml](S23_LSPosed_Module/app/src/main/res/values-cs/strings.xml) translating all 256 companion app, Quick Settings tile, dialog, live Battery card, status bar action, and updater strings.
  - Localized Recents task menu items (`Force stop`, `Restart`, `Clear cache`, `Screenshot`) and all Recents Toast notifications in [RecentsMenuHooks](S23_LSPosed_Module/app/src/main/java/com/s23/enhancements/RecentsMenuHooks.java), and unified `HookEntry.isCzech(Context)` checks across Settings, Launcher, and SystemUI hooks (English remains the default for all other system locales).

### Changed
- Added a screenshots gallery to `README.md` and licensed the repository under GNU GPL v3.0.

## [2.6] - 2026-11-27

### Added
- **Lock Screen & AOD → Charging details** ([ChargingInfoHooks](S23_LSPosed_Module/app/src/main/java/com/s23/enhancements/ChargingInfoHooks.java)):
  - Displays live charging telemetry (`W · mA · V · °C`) directly below One UI's charging percentage and time-to-full line on both the lock screen (`KeyguardIndicationController`) and Always-On Display (`PluginAODManager` / `ChargingInfoView`).
  - Uses Samsung's `current_now` / `current_avg` extras and the S23 Ultra `> 20,000` µA/mA scaling threshold with a live periodic refresh (`2.5 s` interactive / `5 s` AOD) and tabular digits (`tnum`).
- **Recents Task Menu Toolkit** ([RecentsMenuHooks](S23_LSPosed_Module/app/src/main/java/com/s23/enhancements/RecentsMenuHooks.java)):
  - Adds **Force stop**, **Restart**, **Clear cache**, and **Screenshot** directly below **App info** in the One UI Home Recents task popup menu, each individually toggleable under the new **Recents** section in the app (`pref_recents_force_stop`, `pref_recents_restart`, `pref_recents_clear_cache`, `pref_recents_screenshot`).
  - Captures targets via `View#performClick()` + `TaskIconView` without leaking popup `WeakReference`s, clears strictly cache directories on **Clear cache**, and saves clean task snapshots atomically to `Pictures/Screenshots` via `MediaStore` `IS_PENDING` (with split-screen `taskId` matching and `PixelCopy` fallback).
  - Added `com.sec.android.app.launcher` to `scope.list` and `xposed_scope`.

## [2.5] - 2026-11-26

### Added
- **Status Bar → Network speed meter** (`pref_sb_net_speed`): live download/upload speed indicator in the status bar with 3 display styles (`Total speed`, `Download & upload (2 lines)`, `Download only`), tabular digits (`tnum`), automatic clock tint matching, and **Hide when inactive** (`pref_sb_net_speed_auto_hide`, < 1 KB/s), pausing automatically while the screen is off.
- **New One UI 9 Adaptive App Icon** (`mipmap-anydpi-v26/ic_launcher` + `ic_launcher_round`) with a sapphire-to-azure gradient background, One UI sliders + Galaxy sparkle foreground, and `<monochrome>` layer for One UI Color Palette themed icons.

## [2.4] - 2026-11-26

### Added
- **Lock Screen → Lock screen widget rows** (`pref_lockscreen_widget_rows`): expands the One UI 9 lock screen Complication Widget grid from Samsung's 1-row (4-slot) phone limit to **2 rows (up to 8 widgets)** or **3 rows (up to 12 widgets)**.
- Hooks `ComplicationWidgetSizeManager#getRow()` and `ComplicationWidgetSizeManager#isBiggerSize()` across `com.android.systemui`, `com.samsung.android.app.aodservice`, and `com.samsung.android.app.dressroom` (including dynamic plugin `ClassLoader` resolution) so both the live lock screen/AOD and the Wallpaper & Style editor switch to Samsung's 2D multi-row layout rules (`BigComplicationFitSortPositionRule` / `BigComplicationGridDragAndDropRule`).
- Added `com.samsung.android.app.aodservice` and `com.samsung.android.app.dressroom` to `scope.list` and `xposed_scope`.

## [2.3] - 2026-11-25

### Added
- **Status Bar tap/long-press actions**:
  - **Camera** (`camera`): opens Samsung Camera directly from any status bar target.
  - **Open app…** (`app:<packageName>`): pick any installed launcher app (with `<queries>` launcher visibility for Android 11+ and live **Test** support).
- **Native SESL9 (One UI 9) UI** using [sesl-material-components-android](https://github.com/tribalfs/sesl-material-components-android) (`1.14.0+1.0.47-sesl9+rev14`) and [sesl-androidx](https://github.com/tribalfs/sesl-androidx):
  - `CoordinatorLayout` + `AppBarLayout` + `CollapsingToolbarLayout` + `Toolbar` header with DayNight theme support and `meta_998_sesl_app.xml` SESL palette metadata.
  - `PreferenceFragmentCompat` with native grouped `PreferenceCategory`, `SwitchPreferenceCompat`, and `Preference` rows.
  - SESL9 `AlertDialog` and `SeslSeekBar` inside `OneUiDialog`.

### Changed
- Migrated `S23_LSPosed_Module` from the manual `aapt2`/`javac`/`d8` script to Gradle (`9.9.1`) + AGP (`9.5.1`) with `compileSdk = 37` and JDK 24+/25 support, while preserving `debug.keystore` signing and `META-INF/xposed/*` metadata.

## [2.2] - 2026-11-25

### Added
- **Status Bar customization** ([SystemUiHooks](S23_LSPosed_Module/app/src/main/java/com/s23/enhancements/SystemUiHooks.java)):
  - Screen edge spacing (`Samsung's default`, `Tight (4 dp)`, or `Custom 0–32 dp`)
  - Space between items (`0–12 dp` slider + Reset)
  - Custom max notification icons shown (`1–10` slider + Reset, overriding One UI 9's hardcoded 3-icon cap in `NotificationIconContainer`)
  - Choose visible status bar icons (`Settings.Secure` `icon_blacklist`)
  - One connection icon (hide mobile signal icon while Wi-Fi is active)
  - Optional Status Bar Back button (`<`)
  - Burn-in pixel-shift protection toggle (`IndicatorMarqueeGardener` + view shift loop)
- **Status Bar Taps & Feedback** ([StatusBarActions](S23_LSPosed_Module/app/src/main/java/com/s23/enhancements/StatusBarActions.java)):
  - Double-tap empty status bar area to lock screen
  - Configurable tap vibration and strength (`Light`, `Medium`, `Strong`)
  - Tap and long-press actions with live **Test** button for Battery icon (supporting One UI 9's `ComposeView` `battery_meter_composable_view`), Clock, Front camera cutout, Wi-Fi icon, and Mobile signal icon
  - Includes Samsung Device Care `dcapi` + `BatteryControllerImpl` Power Saving toggle
- **Notifications tweaks**:
  - Open notifications fully (`ExpandableNotificationRowController#setSystemExpanded`)
  - Groups open fully with one tap (`ExpandableNotificationRow#setChildrenExpanded`)
  - Keep silent/low-priority notifications collapsed
- **Restart SystemUI** action in the System section (broadcast + `Settings.Global` observer + `su` fallback; reloads SystemUI without rebooting the device)
- **Automatic Updates** ([AppUpdater](S23_LSPosed_Module/app/src/main/java/com/s23/enhancements/AppUpdater.java), [UpdateInstallReceiver](S23_LSPosed_Module/app/src/main/java/com/s23/enhancements/UpdateInstallReceiver.java)):
  - Checks GitHub Releases (`FoxxoOwO/s23-ultimate`) on launch and manually via **Check for updates**
  - Displays scrollable release notes and downloads & installs `.apk` assets via Android `PackageInstaller`

### Changed
- Translated the entire companion app UI (`strings.xml`, Quick Settings tile subtitle, dialogs, code comments), `README.md`, `CHANGELOG.md`, and GitHub Release notes (`v2.0`) to English.
- Filtered `HookEntry` SystemUI hooks strictly to the main `com.android.systemui` process (excluding `:edgelighting` and secondary processes), added `mAppContext` fallback for reliable `Settings.Global` preference reads, and hooked `StatusBarWindowView#dispatchTouchEvent` directly for One UI 9 / Android 17.
- Enhanced `OneUiDialog` with single-choice Test buttons, multi-choice switches, seekbar sliders, and scrollable long confirmation dialogs.

---

## [2.1] - 2026-11-23

### Added
- **4K120 in Slow Motion mode** (`pref_camera_4k120_slowmo`) — resolution map hook in the Camera process.

### Changed
- Translated `README.md` to English.
- Updated `NOTES.md` with 4K120 findings and hook boundaries.

---

## [2.0] - 2026-11-11

### Added
- One UI styled companion app + live **Battery** card (level, status, power source, time until full, temperature, voltage, current, power, charge counter, technology, protection mode).
- Initial 15 LSPosed hooks (LibXposed API 102) across Camera (Log Video, 4K60 lenses, 100% JPEG, Shutter mute), Display (Outdoor Mode + QS Tile, AMOLED Cinema/Photo/Basic profiles, extended timeouts, action timeout, EAD, custom fonts), and System (call recording, Wi-Fi passwords, `FLAG_SECURE` bypass, telemetry disable, detailed battery info).
- Cross-process preference mirroring via `Settings.Global` (`s23enh_*`).
