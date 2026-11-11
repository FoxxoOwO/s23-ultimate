# S23 Ultra Enhancements (LSPosed)

LSPosed modul + doprovodná aplikace v designu Samsung One UI pro **Galaxy S23 Ultra** (SM-S918B, One UI 9 / Android 16–17).

Modul odemyká funkce, které Samsung na S23 softwarově zamkl, a každou z nich lze v aplikaci samostatně zapnout nebo vypnout — bez restartu zařízení.

> ⚠️ **Zařízení se nesmí restartovat**, pokud používáte tethered root (KernelSU / nestálý jailbreak) — restart znamená ztrátu rootu. Po reinstalaci modulu stačí `force-stop` cílové aplikace.

## Funkce

| # | Funkce | Pref klíč |
| :-- | :--- | :--- |
| 1 | Log Video v Pro Video | `pref_camera_log_video` |
| 2 | 4K60 přepínání všech objektivů | `pref_camera_4k60_lenses` |
| 3 | 100% kvalita JPEG | `pref_camera_jpeg_100` |
| 4 | Vypnutí zvuku závěrky | `pref_camera_shutter_mute` |
| 5 | Venkovní režim (odemkne přepínač v Nastavení) | `pref_display_outdoor_mode` |
| 6 | Profily displeje (AMOLED Cinema / Photo / Basic) | `pref_display_screen_mode` |
| 7 | Screen timeouty (30 min, 1 h, Nikdy) | `pref_display_screen_timeout` |
| 8 | Čas pro provedení akce | `pref_display_action_timeout` |
| 9 | Adaptivní barevný tón (EAD) | `pref_display_adaptive_color_tone` |
| 10 | Odemčení fontů | `pref_display_unlock_fonts` |
| 11 | Nahrávání hovorů | `pref_telephony_call_recording` |
| 12 | Zobrazení hesel Wi-Fi | `pref_system_show_wifi_passwords` |
| 13 | Obejití screenshotu (FLAG_SECURE) | `pref_system_disable_flag_secure` |
| 14 | Zakázání telemetrie | `pref_system_disable_telemetry` |
| 15 | Podrobné informace o baterii | `pref_system_battery_info` |

Detailní rozbor každého hooku, zjištěné branky a **co naopak nejde** (Quick Share na iPhone, Now brief/nudge, gesto v rohu) je v [NOTES.md](NOTES.md).

## Architektura

```
Aplikace (One UI)  ──►  SharedPreferences  ──►  zrcadlo do Settings.Global (s23enh_<key>)
                                                        │
                                                        ▼
                          HookEntry (LSPosed) ── čte přes getSystemContext()
                          ├── system_server      (FLAG_SECURE, telemetrie)
                          ├── Kamera             (Log, 4K60, JPEG, závěrka)
                          ├── Nastavení          (displej, timeouty, fonty, baterie, Wi-Fi hesla)
                          ├── SystemUI            (venkovní režim, swipe-up)
                          └── Telefon/InCallUI   (nahrávání hovorů)
```

Proč zrcadlo přes `Settings.Global`: `getRemotePreferences()` vrací na One UI 9 v hookovaných procesech prázdnou mapu a `ActivityThread.currentApplication()` je v `onPackageReady` ještě `null`.

## Sestavení

Požadavky:
- Android SDK (Build-Tools 36, platform android-36), `ANDROID_HOME` nastaven
- JDK 17+
- 7-Zip (`C:\Program Files\7-Zip\7z.exe`)
- ADB (volitelné, pro automatickou instalaci)
- `libxposed-api-102.jar` — stáhněte z [LibXposed API releases](https://github.com/libxposed/api/releases) a položte do kořene repozitáře

```powershell
# build + install na připojené zařízení
powershell -ExecutionPolicy Bypass -File .\build_and_install.ps1

# jen build
powershell -ExecutionPolicy Bypass -File .\build_and_install.ps1 -NoInstall
```

Pipeline: `aapt2 compile/link` → `javac --release 17` → `d8 --min-api 31` → 7-Zip → `zipalign` → `apksigner` → `adb install`.

Podepisuje se `debug.keystore` (pass: `android`, alias `debug`) — pro vlastní build si vygenerujte svůj a upravte cesty ve skriptu.

## Instalace

1. Nainstalujte APK.
2. Udělte oprávnění:
   ```
   adb shell pm grant com.s23.enhancements android.permission.WRITE_SECURE_SETTINGS
   adb shell appops set com.s23.enhancements WRITE_SETTINGS allow
   ```
3. V LSPosed aktivujte modul a nastavte scope: `system`, `android`, `com.sec.android.app.camera`, `com.android.settings`, `com.android.systemui`, `com.samsung.android.incallui`, `com.samsung.android.dialer`, `com.samsung.android.app.telephonyui`, `com.android.phone`.
4. Po změně přepínače v aplikaci `force-stop` cílové aplikace (např. `am force-stop com.sec.android.app.camera`).

## Licence

Bez licence — osobní projekt. Pokud chcete použít části kódu, napište issue.
