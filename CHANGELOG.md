# Changelog

All notable changes to **S23 Ultra Enhancements** are documented in this file.

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
