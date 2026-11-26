# Changelog

All notable changes to **S23 Ultra Enhancements** are documented in this file.

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
