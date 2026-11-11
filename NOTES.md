# Galaxy S23 Ultra Ultimate Enhancements (One UI 8.5 / Android 16)
> **Projekt:** `s23ultimate`  
> **Cílové zařízení:** Samsung Galaxy S23 Ultra (`SM-S918B` / `dm3q` / `kalama` / Snapdragon 8 Gen 2 `SM8550`)  
> **Systémové prostředí:** One UI 8.5 • Android 16 (SDK 36)  
> **Typ řešení:** LSPosed Zygote Module (`LibXposed API 102`) s doprovodnou aplikací v designu Samsung One UI  
> **Kritické provozní pravidlo:** ⚠️ **NIKDY NERESTARTOVAT ZAŘÍZENÍ** (tethered root / KernelSU jailbreak, ztráta rootu při rebootu)

---

## 1. Přehled a účel projektu

Projekt poskytuje sadu systémových, kamerových a zobrazovacích úprav pro **Samsung Galaxy S23 Ultra** na **One UI 8.5 / Android 16**, které nelze zapnout běžným uživatelským rozhraním.

Původně vznikl jako Magisk/KernelSU překryv (`S23_Custom_Enhancer`, modifikace `floating_feature.xml` a `system.prop`). Protože tyto úpravy vyžadovaly restart a nešly za běhu vypínat, byl projekt přepsán do podoby **nativního LSPosed modulu** (`S23_LSPosed_Module` → `S23_LSPosed_Enhancer.apk`) s doprovodnou konfigurační aplikací.

**Rozsah je 14 funkcí** (13 původně požadovaných + vypnutí zvuku závěrky). V aplikaci je 15 přepínačů – 14 featur + odemčení podrobných informací o baterii. Každou z nich lze v aplikaci samostatně povolit nebo zakázat a změna se projeví bez restartu (pouze po `force-stop` cílové aplikace).

---

## 2. Architektura

```
                                  ┌───────────────────────────┐
                                  │   S23 Enhancer (One UI)   │
                                  │  MainActivity + oneui/*   │
                                  └─────────────┬─────────────┘
                                                │ SharedPreferences
                                                ▼
                                  ┌───────────────────────────┐
                                  │       PrefProvider        │
                                  │ (Shared Remote Prefs IPC) │
                                  └─────────────┬─────────────┘
                                                │ + zrcadlo do Settings.Global
                                                │   (`s23enh_<pref_key>` = 0/1)
                                                ▼
                         ┌──────────────────────┼──────────────────────┐
                         ▼                      ▼                      ▼
               ┌──────────────────┐   ┌──────────────────┐   ┌──────────────────┐
               │  Samsung Camera  │   │ SecSettings / UI │   │  System Server   │
               │ (Log, 4K60, JPEG)│   │(Outdoor, mDNIe…) │   │  (FLAG_SECURE)   │
               └──────────────────┘   └──────────────────┘   └──────────────────┘
```

> **Kanál předvoleb mezi procesy (důležité).** `getRemotePreferences()` vrací v hookovaných
> procesech na One UI 9 **prázdnou mapu** a `ActivityThread.currentApplication()` je v okamžiku
> `onPackageReady` ještě `null`, takže se do procesů Kamery/Nastavení/SystemUI nedostane žádná
> hodnota. Proto aplikace po každé změně přepínače (a při startu pro všech 15 klíčů) zapisuje
> zrcadlo do `Settings.Global` pod klíčem `s23enh_<pref_key>` s hodnotou `1`/`0` (aplikace má
> `WRITE_SECURE_SETTINGS`) a `HookEntry.isPref()` čte **nejprve** toto zrcadlo přes
> `ActivityThread.currentActivityThread().getSystemContext()`, teprve pak `getRemotePreferences()`
> a nakonec výchozí hodnotu. Všechny featury mají výchozí stav **zapnuto**.

1. **Zygote injekce (`HookEntry.java`, 1133 řádků)** — `LibXposed API 102`, hooky v `system_server` a v procesech Kamera, Nastavení, SystemUI, Telefon/InCallUI.
2. **Sdílení nastavení (`PrefProvider.java`, `PreferencesHelper.java`)** — `ContentProvider` `com.s23.enhancements.prefs` + `SharedPreferences` `s23_preferences` + zrcadlo `s23enh_*` v `Settings.Global` (viz rámeček výše).
3. **UI (`MainActivity.java` + balíček `ui.oneui`)** — vlastní One UI komponentová vrstva (viz sekce 4), 4 sekce a 15 přepínačů.
4. **Quick Settings dlaždice (`OutdoorModeTileService.java`)** — okamžité zapnutí/vypnutí Venkovního režimu.
5. **Build pipeline (`build_and_install.ps1`)** — AAPT2 → javac 17 → D8 → 7-Zip → zipalign → apksigner → ADB install + grant.

---

## 3. Funkce a hooky (15 položek)

### A. Fotoaparát (`com.sec.android.app.camera`)
| # | Funkce | Pref klíč | Hook |
| :-- | :--- | :--- | :--- |
| 1 | **Log Video** v Pro Video | `pref_camera_log_video` | `floating_feature` tagy `SUPPORT_LOG_VIDEO`, `SUPPORT_FRONT_LOG_VIDEO` |
| 2 | **4K60 přepínání objektivů** (0,6x / 1x / 3x / 10x, Seamless Zoom) | `pref_camera_4k60_lenses` | `ZoomFeature.isPhysicalZoomSupported`, `CameraResolution.getCamcorderPhysicalZoomRecordingAvailableFeature`, `CameraSettings.get(AUTO_LENS_SWITCHING)`, tele-zoom tagy a `FloatTag`/`IntegerTag` mapy |
| 3 | **100% kvalita JPEG** | `pref_camera_jpeg_100` | `CaptureRequest` JPEG_QUALITY=100, `Bitmap.compress(…,100,…)`, `ro.media.enc.jpeg.quality=100` |
| 4 | **Vypnutí zvuku závěrky** | `pref_camera_shutter_mute` | `EnumMap.put` tag `SUPPORT_SHUTTER_SOUND_MENU` → `true`, `Settings.System.getInt(…, "csc_pref_camera_forced_shuttersound_key", …)` → `0`, a v `CameraAudioManager.onResume()` vynulování pole `mForceShutterSound` (obfuskované `a8.v.p`) |

### Poznámka k vypnutí zvuku závěrky (funkce 4)

Samsung umí zvuk závěrky regionálně **vynutit** (Korea, Japonsko a CSC zámek v EU). V Camera 17.0.00.43 to drží dvě nezávislé věci:

| Vrstva | Co to je | Řešení v modulu |
| :-- | :--- | :--- |
| `SUPPORT_SHUTTER_SOUND_MENU` (tag v `Le5/d`) | bez tohoto tagu se volba „Zvuk spouště" v nastavení fotoaparátu vůbec nezobrazí | `EnumMap.put` hook v `hookCamera()` vynutí `true` |
| `csc_pref_camera_forced_shuttersound_key` | `CameraAudioManager.onResume()` čte `Settings.System.getInt(resolver, "csc_pref_camera_forced_shuttersound_key", 0)`; při hodnotě `1` (nebo `IS_COUNTRY_JAPAN` / `IS_COUNTRY_KOREA`) nastaví `mForceShutterSound = true`, a `isPlaySoundAvailable()` pak zablokuje ztlumení | hook `Settings.System.getInt(ContentResolver, String, int)` → pro tento klíč vrací `0` |
| `CameraAudioManager.mForceShutterSound` | pole `p` (`boolean`) v obfuskované třídě `a8.v` | po `onResume()` se reflexí vynuluje (pojistka, kdyby první dvě vrstvy nestačily) |

Ověřeno na SM-S918B 2026-11-07: po zapnutí přepínače se v **Nastavení fotoaparátu → Obecné** objevila položka **„Zvuk spouště"** (dřív tam vůbec nebyla) a v logcatu jsou `Hooked Settings.System.getInt for shutter sound CSC flag` a `Shutter sound: cleared force flag in a8.v`. Zvuk se přestane přehrávat, když je tato volba v fotoaparátu vypnutá — přepínač v aplikaci ji jen odemkne.



### B. Displej (`com.android.settings`, `com.android.systemui`)
| # | Funkce | Pref klíč | Hook |
| :-- | :--- | :--- | :--- |
| 5 | **Venkovní režim** – *pouze zpřístupní přepínač v Nastavení* | `pref_display_outdoor_mode` | `SecOutDoorModePreferenceController.isAvailable/getAvailabilityStatus`, `Rune.supportOutdoorMode`, `BrightnessDetailAdapter` v SystemUI. Přepínač v aplikaci režim sám nezapíná; zapíná se v Nastavení > Displej (nebo QS dlaždicí, která respektuje tento pref) |
| 6 | **Profily displeje** (AMOLED Cinema 0 / Photo 1 / Basic 2) | `pref_display_screen_mode` | `NewModePreview$ScreenModeAdapter` – injektáž `mDBValue` / `mNameStringId` |
| 7 | **Screen timeouty** (30 min, 1 h, Nikdy) | `pref_display_screen_timeout` | `SecDisplayUtils.getScreenTimeoutEntryandValue`, `getTimeoutDescription` |
| 8 | **Čas pro provedení akce** (5 s – 5 min + vlastní) | `pref_display_action_timeout` | `SecAccessibilityControlTimeoutPreferenceFragment.onCreatePreferences`, zápis `accessibility_interactive_ui_timeout_ms` |
| 9 | **Adaptivní barevný tón (EAD)** | `pref_display_adaptive_color_tone` | `SecEADPreferenceController.getAvailabilityStatus` → `0` |
| 10 | **Odemčení fontů** | `pref_display_unlock_fonts` | `SecDisplayUtils.isInvalidFont` → `false` |

### C. Systém
| # | Funkce | Pref klíč | Hook |
| :-- | :--- | :--- | :--- |
| 11 | **Nahrávání hovorů** | `pref_telephony_call_recording` | `SemCscFeature.getString(CscFeature_VoiceCall_ConfigRecording)` → `"RecordingAllowed"` (InCallUI, Dialer, TelephonyUI, Phone) |
| 12 | **Zobrazení hesel Wi-Fi** | `pref_system_show_wifi_passwords` | `WifiUtils.isSupportVerifyingForPassword` → `false`; `WifiDetailPreferenceController.displayPreference` odemkne `mPasswordView` a skryje `mPasswordShowButton` |
| 13 | **Obejití screenshotu** | `pref_system_disable_flag_secure` | `WindowState.isSecureLocked` → `false`, `SurfaceControl$Builder.setSecure` → `false` |
| 14 | **Zakázání telemetrie** | `pref_system_disable_telemetry` | `SystemProperties`: `profiler.force_disable_err_rpt`, `profiler.force_disable_ulog` |
| 15 | **Podrobné informace o baterii** | `pref_system_battery_info` | `BatteryInfoUtils.isAvailableBatteryRegulatory()` → `true`; `getAvailabilityStatus()` → `0` + `supportBatteryCapacity()` → `true` na 7 kontrolerech (detail v sekci 3.D.2) |


### D. Baterie – karta v aplikaci + odemčení detailů v systému

Aplikace má **kartu Baterie** hned pod hlavičkou a na jejím konci jeden přepínač, který odemyká skryté řádky v systémovém Nastavení.

#### D.1 Karta Baterie (jen čtení, živě se aktualizuje)

Data nejdou číst ze sysfs – `/sys/class/power_supply/battery/*` je pro shell/uid 2000 **Permission denied** (`ls` i `cat` selžou, `adb root` na tomto zařízení není). Všechno tedy pochází ze sticky broadcastu `ACTION_BATTERY_CHANGED` + `BatteryManager`.

| Řádek | Zdroj | Poznámka |
| :--- | :--- | :--- |
| Úroveň | `EXTRA_LEVEL` / `EXTRA_SCALE`, fallback `BATTERY_PROPERTY_CAPACITY` | „45 %" |
| Stav | `EXTRA_STATUS` | „Nabíjí se" se zobrazuje v akcentní modré |
| Napájení | `EXTRA_PLUGGED` | Síťový adaptér / USB / Bezdrátově / Dok / Bez napájení |
| Zbývá do plného nabití | `BatteryManager.computeChargeTimeRemaining()` | jen při `STATUS_CHARGING` a kladné hodnotě; formát „7 h 9 min" |
| Zdraví | `EXTRA_HEALTH` | Dobrá / Přehřátá / Vadná / Přepětí / Závada / Podchlazená |
| Teplota | `EXTRA_TEMPERATURE` (desetiny °C) | `%.1f °C` |
| Napětí | `EXTRA_VOLTAGE` (mV) | `%.2f V` |
| Proud | Samsung extra `current_now` / `current_avg` (mA); fallback `BATTERY_PROPERTY_CURRENT_NOW` (µA) | **pozor:** veřejná property vrací µA, proto se dělí 1000 jen když je |hodnota| > 20000 |
| Výkon | napětí × proud | `%.1f W` |
| Nabití | `BATTERY_PROPERTY_CHARGE_COUNTER` (µAh) | zobrazuje se v mAh (`/1000`) |
| Technologie | `EXTRA_TECHNOLOGY` | Li-ion |
| Ochrana baterie | `Settings.Global protect_battery` + `battery_protection_threshold` | `0` = Vypnuto, `4` = „Adaptivní (80 %)", jiná hodnota = „Zapnuto (x %)" |

Implementace: `MainActivity.buildBatterySection()` + `updateBattery(Intent)`; receiver se registruje v `onResume()` přes `registerReceiver(mBatteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED))` (vrací sticky Intent, takže se karta vykreslí hned) a odregistruje v `onPause()`.

**Poznámka k cyklům:** `EXTRA`/extra `cycle_count` je na tomto zařízení **vždy 0**, takže řádek „Cykly" byl z karty odstraněn. Počet cyklů jde zjistit jen z privátního EFS uzlu `/efs/FactoryApp/batt_discharge_level` (viz D.3), a to pouze v procesu, který má na EFS právo.

#### D.2 Přepínač „Podrobné informace o baterii"

Pref klíč `pref_system_battery_info`, hook `HookEntry.hookBatteryInfo()`.

Samsung na S23 schovává celý blok „EU regulatory" informací o baterii. Všechny ty řádky dědí z `BatteryRegulatoryPreferenceController`, jehož `getAvailabilityStatus()` volá `BatteryInfoUtils.isAvailableBatteryRegulatory(Context)`:

```java
// BatteryInfoUtils.isAvailableBatteryRegulatory(Context)
if (Rune.FEATURE_BATTERY_INFO_EU_REGULATORY_WITHOUT_BATTERY_IC && Rune.isDomesticModel()) return true;
if (!batteryManager.semGetValueAsBoolean(106)) {                 // 106 = "battery IC present"
    if (SecAboutDeviceItems.ABOUTITEM_STATUS_EU_ECO_DESIGN == 1) return true;
    if (Rune.FEATURE_BATTERY_INFO_EU_REGULATORY_ALL_REGION) return true;
}
return Rune.FEATURE_BATTERY_INFO_EU_REGULATORY && BatteryInfoUtils.mIsCountryUnderEcoRegulatory;
```

Na S23 jsou všechny ty `Rune` flagy `false` a `semGetValueAsBoolean(106)` také, takže celý blok je `GONE`.

Hook proto dělá dvě věci:
1. `BatteryInfoUtils.isAvailableBatteryRegulatory(...)` → `true` (jediná brána pro všechny potomky)
2. pro jistotu `getAvailabilityStatus()` → `0` a `supportBatteryCapacity()` → `true` na 7 třídách: `BatteryRegulatoryPreferenceController` (báze), `BatteryHealthPreferenceController`, `BatteryInfoCapacityPreferenceController`, `BatteryInfoCapacityTypicalDescPreferenceController`, `SecBatteryCycleCountPreferenceController`, `SecBatteryFirstUseDatePreferenceController`, `SecBatteryManufactureDatePreferenceController`

Výsledek v **Nastavení → Baterie → Informace o baterii** (ověřeno na SM-S918B 2026-11-07, oba směry):

| Přepínač | Co je na obrazovce |
| :--- | :--- |
| ON | Stav baterie, Nabití baterie, Kapacita baterie + **Zdraví baterie (95 %)**, **Počet cyklů baterie (834)**, **Datum výroby (12. května 2023)**, **Datum prvního použití (03. srpna 2023)** |
| OFF | jen Stav / Nabití / Kapacita |

V logcatu při zapnutí: `Battery info: isAvailableBatteryRegulatory() -> true` a `Battery info: unlocked 7 battery-info controllers`.

#### D.3 EFS / sysfs uzly, ze kterých ty řádky čtou

Řádky nejsou prázdné, protože by data neexistovala – existují, ale leží v privátních souborech. Načítá je `BatteryRegulatoryPreferenceController.readNode(path)` / `readNodeAsInt(path)`; pro shell je `cat` na těchto cestách **Permission denied**, takže se hodnoty v aplikaci nedají číst (a ani se to nepokoušíme).

| Řádek v Nastavení | Zdroj |
| :--- | :--- |
| Počet cyklů baterie | `/efs/FactoryApp/batt_discharge_level` (fallback `_2nd`, `_3rd`), hodnota se dělí `100` (`getCycleCountInfo`) |
| Datum prvního použití | `/efs/FactoryApp/batt_beginning_date` (fallback `_2nd`, `_3rd`), formát `yyyyMMdd`, `parseFirstUseDate` |
| Datum výroby | `/efs/FactoryApp/HwParamBattQR` (fallback `_2nd`, `_3rd`), `parseManufactureDate` + `mManufactureYearMap` |
| Zdraví baterie (BSOH) | `BatteryManager.semGetValuesAsLong(107)` (`SEM_BATTERY_PROPERTY_BSOH`), `div-int/lit8 v0, v0, #100`, formátováno `Utils.formatPercentage` |
| Kapacita baterie | `BatteryInfoCapacityPreferenceController` + `supportBatteryCapacity()` (váže se na `ABOUTITEM_STATUS_BATTERY_CAPACITY` a `SEC_FLOATING_FEATURE_SETTINGS_CONFIG_BRAND_NAME`) |
| Baterie bez IC | `BatteryManager.semGetValueAsBoolean(106)` a `BatteryInfoUtils.mBatteryCount` (počítá se z existence `/sys/class/power_supply/sec_auth*/qr_code` a `sbp-fg*/qr_code`) |

`BatteryInfoUtils.mBatteryCount` a `mBatteryType` se plní ve statickém inicializátoru z existence těch `qr_code` uzlů; `mIsCountryUnderEcoRegulatory` z CSC. To je důvod, proč hodnoty „svítí" i po odemknutí — čtou se v procesu `com.android.settings`, který na EFS právo má.

### Gesto v rohu pro otevření asistenta – NEFUNGUJE (analýza 2026-11-07)

**Nezkoušet znovu.** Tažení z dolního rohu obrazovky pro otevření asistenta (Gemini / Bixby) se na S23 Ultra obnovit nedá, i když se podaří odemknout všechny tři brány v SystemUI. Blokuje to Samsung launcher na úrovni InputDispatcheru, kam LSPosed hook v SystemUI nedosáhne.

**Tři brány, které šly odemknout** (`NavBarHelper.updateAssistantAvailability()` v SystemUI):

| # | Brána | Původně | Po hooku |
| :-- | :--- | :--- | :--- |
| 1 | `NavBarHelper.mAssistantAvailable` | `false` | ✅ `true` |
| 2 | `NavBarHelper.mLongPressHomeEnabled` | `false` | ✅ `true` |
| 3 | `QuickStepContract.isAssistantGestureDisabled()` | `true` | ✅ `false` |

Zdroj hodnot (dekompilace `SystemUI.apk` → `classes2.dex`, `com.android.systemui.navigationbar.NavBarHelper`):
- `mAssistantTouchGestureEnabled` = `Settings.Secure.getIntForUser("assist_touch_gesture_enabled") != 0` (na S23 je `1`)
- `mLongPressHomeEnabled` = `Settings.Secure.getIntForUser("assist_long_press_home_enabled") != 0` (na S23 je `1`, ale pole přesto zůstávalo `false`); hodnotu dále modifikuje větev `BasicRune.NAVBAR_ENABLED` / `BasicRune.SUPPORT_AI_AGENT`
- `mAssistantAvailable` = `AssistUtils.getAssistComponentForUser() != null` **&&** `mAssistantTouchGestureEnabled` **&&** `!<boolean z resources>` **&&** `(BasicRune.SUPPORT_AI_AGENT ? (getNavigationBarSPluginFlags() & FLAG_NAVSTAR_ASSISTANT_ENABLED) == 0 : true)` **&&** `QuickStepContract.isGesturalMode(mNavBarMode)`
  - poznámka: nastavení `Settings.Secure navigationbar_splugin_flags = 8` bránu naopak **zavře** (`and-int 8` → `if-eqz` → false), proto se v testu nechal `null`
- `CornerGestureHandler.updateIsEnabled()` přiloží `InputMonitor` jen když je `mAssistantAvailable == true`, a v `onTouchEvent` navíc kontroluje `QuickStepContract.isAssistantGestureDisabled(sysUiFlags)`

Test proběhl tak, že se v `updateAssistantAvailability()` po `chain.proceed()` forceovala pole `mAssistantAvailable=true` a `mLongPressHomeEnabled=true` (reflexe), `isAssistantGestureDisabled()` vracelo `false` a v `CornerGestureHandler.updateIsEnabled()` se po `proceed()` nastavilo `allowGesture=true`.

Po odemknutí všech tří bran a restartu SystemUI:
- log z hooků: `Assistant: availability forced on NavBarHelper@…`
- `dumpsys activity service com.android.systemui/.SystemUIService` → `mAssistantAvailable=true`, `longPressHomeEnabled=true`
- `dumpsys input` → `[Gesture Monitor] CornerGestureMonitor` existuje, `responsive=true`, `ownerUid=1000`

**Proč to přesto nefunguje** – dotyk corner handleru sebere launcher:

```
InputDispatcher: Channel [Gesture Monitor] swipe-up is stealing input gesture for device -1
                 from […, [Gesture Monitor] CornerGestureMonitor]
input_cancel:    [[Gesture Monitor] CornerGestureMonitor, reason=input channel stole pointer stream]
```

`[Gesture Monitor] swipe-up` vlastní **`com.sec.android.app.launcher`** (HoneySpace, `ownerUid=10165`), ne SystemUI. Samsung launcher má vlastní frameworkový gesture monitor s vyšší prioritou („swipe up" pro hledání / Recents) a má `touchableRegion` přes celou obrazovku, takže vyhraje nad monitorem corner handleru. Gesto se tedy zaregistruje, ale nikdy nedostane event.

**Co by to teoreticky vyřešilo (a proč jsme to nedělali):**
- hook přímo v `com.sec.android.app.launcher` na potlačení `swipe-up` stealu v oblasti rohu → riziko rozbití swipe-up gest launcheru (Recents, hledání), výrazně vyšší riziko než ostatní featury
- změna priority / `touchableRegion` gesture monitoru v `system_server` → ještě invazivnější

**Pročištění po testech:** `HookEntry.hookAssistantCorner()`, `forceBooleanField()`, konstanta `PREF_ASSISTANT_CORNER`, přepínač v `MainActivity` i řetězce byly z kódu odstraněny; `Settings.Global s23enh_pref_system_assistant_corner` smazán; `Settings.Secure navigationbar_splugin_flags` vrácen na `null` (původní stav) a `assist_long_press_home_enabled` ponechán na `1` (původní hodnota). Po restartu SystemUI je stav zpět `mAssistantAvailable=false`, `longPressHomeEnabled=false`.

### Odstraněné funkce (záměrně, na žádost)
24 MPx režim, APV kodek, 3D LUT, Auto HDR, zvuk závěrky, blesk při slabé baterii, neomezené video, nulová prodleva závěrky, Knox bypass (S Health / Notes / SmartThings), SMS→MMS, sémantické vyhledávání v Galerii, barevná korekce Log videa, Vulkan renderer, varování o hlasitosti, hooky pro Zprávy / Fotoeditor / Knox aplikace.

> **Gesto v rohu pro asistenta** bylo vyzkoušeno 2026-11-07 a odstraněno – tři brány v SystemUI šly odemknout, ale dotyk sebere `swipe-up` gesture monitor Samsung launcheru (viz sekce výše).
>
> **Quick Share s iPhony, Now brief a Now nudge** byly 2026-11-05 z nabídky odstraněny také. Quick Share UI (řádek „Sdílet se zařízeními Apple“) se podařilo odemknout, ale reálný přenos na iPhone neprojde – GMS Nearby na S23 nemá AWDL medium (`libwifi-hal-qcom.so` neobsahuje `awdl`, `pm list features` nabízí jen `android.hardware.wifi.aware`) a Java hook chybějící firmware nenahradí. Funguje jen QR kód / odkaz. Now brief / Now nudge sice v Nastavení → Galaxy AI zobrazí řádek, ale obsah potřebuje Personal data intelligence (PDE), které na S23 není nainstalované – funkce tedy jen „svítí“, nic nedělá.
>
> Historická analýza: APV na S23 selhává, protože Qualcomm Chi ISP produkuje `YCbCr_420_SP_VENUS_UBWC`, který softwarový `c2.android.apv.encoder` neumí načíst (`getPlaneLayout()` → `C2_BAD_VALUE`). 24 MPx vyžaduje ISP pipeline S24/S25, na S23 shodí náhled. Proto byly obě funkce z nabídky odstraněny úplně.

---

## 4. Komponentová vrstva One UI (`ui.oneui`)

UI není postavené na XML+Spinner, ale na vlastních komponentách, které se chovají jako One UI a přitom nevyžadují AndroidX/Compose (build zůstává čistě AAPT2 + javac).

| Třída | Role |
| :--- | :--- |
| `OneUiTheme` | Design tokeny – barvy (`#000000`, `#1C1C1E`, `#27292E`, `#8E8E93`, `#2388FF`), velikosti písma, `dp()`/`sp()` |
| `OneUiSwitch` | Vlastní `View` – 52×32 dp track, 13 dp jezdec, `ValueAnimator` 160 ms, API kompatibilní s `CompoundButton.OnCheckedChangeListener` |
| `OneUiPreferenceRow` | Řádek s přepínačem: titulek, popis, klik kamkoliv, 1px oddělovač s 18dp odsazením |
| `OneUiSelectRow` | Řádek s hodnotou a chevronem (výběr profilu / timeoutu) |
| `OneUiInfoRow` | Read-only řádek pro detailní karty: label nahoře, šedá hodnota pod ním (stejný pattern jako Samsung „Stav baterie / Nabíjí se (USB)"). Bez ripple a bez chevronu. `setValue()` zobrazí prázdnou hodnotu jako `—`, `setValueAccent()` obarví hodnotu akcentně |
| `OneUiPreferenceGroup` | Zaoblená karta (26dp); poslednímu dítěti automaticky skryje oddělovač |
| `OneUiSectionHeader` | Modrý nadpis sekce |
| `OneUiDeviceCard` | Hero karta zařízení se stavovou kapslí (zelená = podporované zařízení) |
| `OneUiRadioDot` | Vlastní radio indikátor v dialogu |
| `OneUiDialog` | `showSingleChoice()` a `showInput()` – tmavý 28dp dialog, `Cancel` řádek, IME pro vlastní hodnotu |

**Pravidlo pro tento build pipeline:** každý vlastní `View` **musí** mít konstruktor `(Context, AttributeSet)`, který předává `attrs` do `super(...)`. Pokud ne, `LayoutInflater` použije jednoargumentový konstruktor, zahodí atributy a `android:id` z XML zmizí (`findViewById` vrátí `null`).

---

## 5. Souborová struktura

```
d:\repositories\s23ultimate\
├── build_and_install.ps1             # Build & install skript (AAPT2 → javac → D8 → 7z → sign → adb)
├── debug.keystore                    # Vývojový podpisový klíč
├── libxposed-api-102.jar             # LibXposed compile-time knihovna
├── S23_LSPosed_Enhancer.apk          # Výsledná podepsaná APK
├── S23_Custom_Enhancer\              # Původní Magisk/KernelSU modul (historická reference)
├── oneui-design-ref\                 # OneUIProject/oneui-design (vizuální reference)
└── S23_LSPosed_Module\app\src\main\
    ├── AndroidManifest.xml           # Oprávnění, QS dlaždice, provider
    ├── java\com\s23\enhancements\
    │   ├── HookEntry.java            # Všechny LSPosed hooky (1133 řádků)
    │   ├── PrefProvider.java         # ContentProvider pro IPC sdílení voleb
    │   ├── PreferencesHelper.java
    │   └── ui\
    │       ├── MainActivity.java     # Obrazovka aplikace (sekce Fotoaparát/Displej/Systém)
    │       ├── OutdoorModeTileService.java
    │       └── oneui\                # One UI komponentová vrstva (10 tříd, vč. OneUiInfoRow)
    ├── resources\META-INF\xposed\    # module.prop, scope.list, java_init.list
    └── res\
        ├── drawable\                 # oneui_card_bg, oneui_card_hero_bg, oneui_dialog_bg, oneui_row_ripple, oneui_input_bg, oneui_status_*, ic_sun
        ├── layout\                   # activity_main.xml (hlavička + hero karta; řádky se staví v kódu)
        └── values\                   # colors.xml, strings.xml (čeština), styles.xml, arrays.xml
```

---

## 6. Sestavení a nasazení

### Požadavky
- Android SDK Build-Tools (skript bere nejnovější; ověřeno s `36.0.0`), `ANDROID_HOME` = `D:\Android\SDK`
- JDK s podporou `--release 17`
- 7-Zip (`C:\Program Files\7-Zip\7z.exe`)
- ADB + připojené zařízení

### Příkaz
```powershell
powershell -ExecutionPolicy Bypass -File "d:\repositories\s23ultimate\build_and_install.ps1"
# pouze build bez instalace:
powershell -ExecutionPolicy Bypass -File "d:\repositories\s23ultimate\build_and_install.ps1" -NoInstall
```

Skript: vyčistí `build_output` → `aapt2 compile/link` → `javac --release 17` → `d8 --min-api 31` → 7-Zip (DEX + `META-INF/xposed`) → `zipalign` → `apksigner` → `adb install -r` + `pm grant WRITE_SECURE_SETTINGS` + `appops set WRITE_SETTINGS allow`.

---

## 7. Aktuální stav

| Komponenta | Stav | Poznámka |
| :--- | :--- | :--- |
| **HookEntry (14 funkcí + baterie)** | ✅ Hotovo | Ostatní hooky odstraněny; experimentální Quick Share / Now brief / Now nudge odebrány jako nefunkční |
| **One UI komponentová vrstva** | ✅ Hotovo | 10 tříd v `ui.oneui` (včetně `OneUiInfoRow`), bez AndroidX/Compose |
| **Karta Baterie** | ✅ Hotovo | Read-only, 12 řádků, živě z `ACTION_BATTERY_CHANGED` |
| **Podrobné informace o baterii** | ✅ Hotovo | Gate `BatteryInfoUtils.isAvailableBatteryRegulatory`; na S23 se objeví Zdraví 95 %, 834 cyklů, data výroby a prvního použití |
| **MainActivity + layout** | ✅ Hotovo | 4 sekce (Baterie, Fotoaparát, Displej, Systém), 15 přepínačů, 3 výběrové řádky, dialogová volba |
| **Scope modulu** | ✅ Zúženo | `system`, `android`, Kamera, Nastavení, SystemUI, InCallUI, Dialer, TelephonyUI, Phone |
| **Build pipeline** | ✅ Funkční | Generuje podepsanou `S23_LSPosed_Enhancer.apk` |
| **Ověření UI** | ✅ Emulátor | Android 15 / 1080×2400, bez pádů, přepínače se ukládají |
| **Ověření na S23 Ultra** | ✅ | SM-S918B, One UI 9.0; kanál `s23enh_*` v `Settings.Global` ověřen (4K60 přepínač i Log Video se propisují, bez restartu) |
| **Quick Share / Now brief / Now nudge** | ❌ Odebráno | Nejdou na S23 rozchodit (chybí AWDL v HAL, resp. PDE) → přepínače odstraněny z aplikace |
| **Gesto v rohu pro asistenta** | ❌ Odebráno | Tři brány v SystemUI odemknutelné, ale `swipe-up` monitor launcheru krade input → přepínač odstraněn |

---

## 8. Kritická provozní upozornění
- 🔴 **Zařízení se nesmí restartovat!** Root je v tethered / nestálém režimu; restart by znamenal ztrátu rootu.
- 🟡 Po reinstalaci modulu stačí pro načtení změn **vynutit ukončení cílové aplikace** (`am force-stop com.sec.android.app.camera` / `com.android.settings`), restart systému není potřeba.
- 🟡 Změna v aplikaci se do hooků propíše okamžitě (zrcadlo `s23enh_*` v `Settings.Global`), ale cílová aplikace si hodnotu načte při svém startu.
