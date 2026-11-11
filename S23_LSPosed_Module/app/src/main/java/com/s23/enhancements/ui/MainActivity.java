package com.s23.enhancements.ui;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.s23.enhancements.PrefProvider;
import com.s23.enhancements.R;
import com.s23.enhancements.ui.oneui.OneUiDeviceCard;
import com.s23.enhancements.ui.oneui.OneUiDialog;
import com.s23.enhancements.ui.oneui.OneUiInfoRow;
import com.s23.enhancements.ui.oneui.OneUiPreferenceGroup;
import com.s23.enhancements.ui.oneui.OneUiPreferenceRow;
import com.s23.enhancements.ui.oneui.OneUiSectionHeader;
import com.s23.enhancements.ui.oneui.OneUiSelectRow;
import com.s23.enhancements.ui.oneui.OneUiTheme;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Single screen of the companion app. Every supported feature is one row and can be
 * switched on or off independently; the LSPosed hook reads the same keys.
 */
public class MainActivity extends Activity {

    // Camera
    public static final String KEY_CAMERA_LOG_VIDEO = "pref_camera_log_video";
    public static final String KEY_CAMERA_4K60_LENSES = "pref_camera_4k60_lenses";
    public static final String KEY_CAMERA_JPEG_100 = "pref_camera_jpeg_100";
    public static final String KEY_CAMERA_SHUTTER_MUTE = "pref_camera_shutter_mute";

    // Display
    public static final String KEY_DISPLAY_OUTDOOR = "pref_display_outdoor_mode";
    public static final String KEY_DISPLAY_SCREEN_MODE = "pref_display_screen_mode";
    public static final String KEY_DISPLAY_SCREEN_TIMEOUT = "pref_display_screen_timeout";
    public static final String KEY_DISPLAY_ACTION_TIMEOUT = "pref_display_action_timeout";
    public static final String KEY_DISPLAY_EAD = "pref_display_adaptive_color_tone";
    public static final String KEY_DISPLAY_FONTS = "pref_display_unlock_fonts";

    // System
    public static final String KEY_SYSTEM_CALL_RECORDING = "pref_telephony_call_recording";
    public static final String KEY_SYSTEM_WIFI_PASSWORDS = "pref_system_show_wifi_passwords";
    public static final String KEY_SYSTEM_FLAG_SECURE = "pref_system_disable_flag_secure";
    public static final String KEY_SYSTEM_TELEMETRY = "pref_system_disable_telemetry";

    // Battery
    public static final String KEY_BATTERY_INFO = "pref_system_battery_info";

    private static final int[] SCREEN_MODE_VALUES = {4, 3, 0, 1, 2};
    private static final long[] SCREEN_TIMEOUT_VALUES = {
            15000L, 30000L, 60000L, 120000L, 300000L, 600000L, 1800000L, 3600000L, Integer.MAX_VALUE
    };
    private static final int[] ACTION_TIMEOUT_VALUES = {
            0, 5000, 10000, 20000, 30000, 60000, 120000, 300000, -1
    };

    /** Mirror prefix — must match HookEntry.GLOBAL_PREFIX. */
    private static final String GLOBAL_PREFIX = "s23enh_";

    private static final String[] ALL_KEYS = {
            KEY_CAMERA_LOG_VIDEO, KEY_CAMERA_4K60_LENSES, KEY_CAMERA_JPEG_100,
            KEY_CAMERA_SHUTTER_MUTE,
            KEY_DISPLAY_OUTDOOR, KEY_DISPLAY_SCREEN_MODE, KEY_DISPLAY_SCREEN_TIMEOUT,
            KEY_DISPLAY_ACTION_TIMEOUT, KEY_DISPLAY_EAD, KEY_DISPLAY_FONTS,
            KEY_SYSTEM_CALL_RECORDING, KEY_SYSTEM_WIFI_PASSWORDS,
            KEY_SYSTEM_FLAG_SECURE, KEY_SYSTEM_TELEMETRY,
            KEY_BATTERY_INFO
    };

    private SharedPreferences mPrefs;

    // Battery details card
    private OneUiInfoRow mBattLevelRow;
    private OneUiInfoRow mBattStatusRow;
    private OneUiInfoRow mBattSourceRow;
    private OneUiInfoRow mBattRemainingRow;
    private OneUiInfoRow mBattHealthRow;
    private OneUiInfoRow mBattTempRow;
    private OneUiInfoRow mBattVoltageRow;
    private OneUiInfoRow mBattCurrentRow;
    private OneUiInfoRow mBattPowerRow;
    private OneUiInfoRow mBattChargeRow;
    private OneUiInfoRow mBattTechRow;
    private OneUiInfoRow mBattProtectRow;
    private boolean mBatteryReceiverRegistered;

    /** Live battery values - the system re-broadcasts ACTION_BATTERY_CHANGED on every change. */
    private final BroadcastReceiver mBatteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
                updateBattery(intent);
            }
        }
    };

    private OneUiSelectRow mScreenModeRow;
    private OneUiSelectRow mScreenTimeoutRow;
    private OneUiSelectRow mActionTimeoutRow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(OneUiTheme.BG);
        getWindow().setNavigationBarColor(OneUiTheme.BG);
        setContentView(R.layout.activity_main);

        mPrefs = getSharedPreferences(PrefProvider.PREF_NAME, Context.MODE_PRIVATE);

        bindDeviceCard();
        buildBatterySection();
        buildCameraSection();
        buildDisplaySection();
        buildSystemSection();
        addFooter();

        updatePermissions();
        syncAllPrefsToGlobal();
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerBatteryReceiver();
        syncScreenModeFromSystem();
        syncScreenTimeoutFromSystem();
        syncActionTimeoutFromSystem();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mBatteryReceiverRegistered) {
            try {
                unregisterReceiver(mBatteryReceiver);
            } catch (Throwable ignored) {
            }
            mBatteryReceiverRegistered = false;
        }
    }

    // ------------------------------------------------------------------ sections

    /**
     * Read-only card with live battery details (level, state, temperature, voltage,
     * current, power, charge counter, cycles, protection mode).
     */
    private void buildBatterySection() {
        LinearLayout root = findViewById(R.id.content_root);
        root.addView(new OneUiSectionHeader(this, getString(R.string.sec_battery)));

        OneUiPreferenceGroup group = new OneUiPreferenceGroup(this);
        mBattLevelRow = infoRow(group, R.string.batt_level);
        mBattStatusRow = infoRow(group, R.string.batt_status);
        mBattSourceRow = infoRow(group, R.string.batt_source);
        mBattRemainingRow = infoRow(group, R.string.batt_remaining);
        mBattHealthRow = infoRow(group, R.string.batt_health);
        mBattTempRow = infoRow(group, R.string.batt_temp);
        mBattVoltageRow = infoRow(group, R.string.batt_voltage);
        mBattCurrentRow = infoRow(group, R.string.batt_current);
        mBattPowerRow = infoRow(group, R.string.batt_power);
        mBattChargeRow = infoRow(group, R.string.batt_charge);
        mBattTechRow = infoRow(group, R.string.batt_tech);
        mBattProtectRow = infoRow(group, R.string.batt_protect);
        group.addView(toggleRow(KEY_BATTERY_INFO, R.string.title_battery_info,
                R.string.desc_battery_info, true));
        root.addView(group);

        updateBattery(null);
    }

    private OneUiInfoRow infoRow(OneUiPreferenceGroup group, int titleRes) {
        OneUiInfoRow row = new OneUiInfoRow(this).title(getString(titleRes));
        group.addView(row);
        return row;
    }

    private void registerBatteryReceiver() {
        try {
            Intent sticky = registerReceiver(mBatteryReceiver,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            mBatteryReceiverRegistered = true;
            if (sticky != null) {
                updateBattery(sticky);
            }
        } catch (Throwable ignored) {
        }
    }

    /** Renders every battery row. {@code intent} may be null (static-only refresh). */
    private void updateBattery(Intent intent) {
        if (mBattLevelRow == null) {
            return;
        }
        BatteryManager bm = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);

        int level = -1;
        int scale = 100;
        int status = BatteryManager.BATTERY_STATUS_UNKNOWN;
        int health = BatteryManager.BATTERY_HEALTH_UNKNOWN;
        int plugged = 0;
        int voltageMv = 0;
        int temperature = 0;
        String technology = null;
        long chargeCounterUah = 0L;

        if (intent != null) {
            level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            status = intent.getIntExtra(BatteryManager.EXTRA_STATUS,
                    BatteryManager.BATTERY_STATUS_UNKNOWN);
            health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH,
                    BatteryManager.BATTERY_HEALTH_UNKNOWN);
            plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
            temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
            technology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);
        }
        if (level < 0 && bm != null) {
            level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        }
        if (bm != null) {
            int counter = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
            if (counter > 0) {
                chargeCounterUah = counter;
            }
        }
        if (scale <= 0) {
            scale = 100;
        }

        int percent = level < 0 ? -1 : Math.round(level * 100f / scale);
        mBattLevelRow.setValue(percent < 0 ? null : percent + " %");

        mBattStatusRow.setValue(statusLabel(status));
        mBattStatusRow.setValueAccent(status == BatteryManager.BATTERY_STATUS_CHARGING);
        mBattSourceRow.setValue(sourceLabel(plugged));
        mBattHealthRow.setValue(healthLabel(health));

        if (temperature > 0) {
            mBattTempRow.setValue(String.format(Locale.getDefault(), "%.1f \u00B0C",
                    temperature / 10f));
        } else {
            mBattTempRow.setValue(null);
        }

        if (voltageMv > 0) {
            mBattVoltageRow.setValue(String.format(Locale.getDefault(), "%.2f V",
                    voltageMv / 1000f));
        } else {
            mBattVoltageRow.setValue(null);
        }

        long currentMa = 0L;
        // Samsung ships the real values as extras (already in mA); the public
        // BatteryManager properties report microamps and are only a fallback.
        if (intent != null) {
            int extraNow = intent.getIntExtra("current_now", 0);
            int extraAvg = intent.getIntExtra("current_avg", 0);
            if (extraNow != 0) {
                currentMa = extraNow;
            } else if (extraAvg != 0) {
                currentMa = extraAvg;
            }
        }
        if (currentMa == 0L && bm != null) {
            int now = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            if (now == Integer.MIN_VALUE || now == 0) {
                now = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE);
            }
            if (now != Integer.MIN_VALUE && now != 0) {
                currentMa = Math.abs(now) > 20000L ? now / 1000L : now;
            }
        }
        long absCurrentMa = Math.abs(currentMa);
        mBattCurrentRow.setValue(currentMa == 0L ? null : absCurrentMa + " mA");

        if (voltageMv > 0 && absCurrentMa > 0) {
            double watts = (voltageMv / 1000.0) * (absCurrentMa / 1000.0);
            mBattPowerRow.setValue(String.format(Locale.getDefault(), "%.1f W", watts));
        } else {
            mBattPowerRow.setValue(null);
        }

        mBattChargeRow.setValue(chargeCounterUah <= 0 ? null
                : (chargeCounterUah / 1000L) + " mAh");

        mBattTechRow.setValue(technology);

        long remaining = -1L;
        if (bm != null) {
            try {
                remaining = bm.computeChargeTimeRemaining();
            } catch (Throwable ignored) {
            }
        }
        if (status == BatteryManager.BATTERY_STATUS_CHARGING && remaining > 0) {
            long minutes = remaining / 60000L;
            mBattRemainingRow.setValue(minutes >= 60
                    ? getString(R.string.batt_hours_minutes, minutes / 60, minutes % 60)
                    : getString(R.string.batt_minutes, minutes));
        } else {
            mBattRemainingRow.setValue(null);
        }

        mBattProtectRow.setValue(protectionLabel());
    }

    private String statusLabel(int status) {
        switch (status) {
            case BatteryManager.BATTERY_STATUS_CHARGING:
                return getString(R.string.batt_status_charging);
            case BatteryManager.BATTERY_STATUS_DISCHARGING:
                return getString(R.string.batt_status_discharging);
            case BatteryManager.BATTERY_STATUS_FULL:
                return getString(R.string.batt_status_full);
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING:
                return getString(R.string.batt_status_not_charging);
            default:
                return getString(R.string.batt_status_unknown);
        }
    }

    private String sourceLabel(int plugged) {
        switch (plugged) {
            case BatteryManager.BATTERY_PLUGGED_AC:
                return getString(R.string.batt_src_ac);
            case BatteryManager.BATTERY_PLUGGED_USB:
                return getString(R.string.batt_src_usb);
            case BatteryManager.BATTERY_PLUGGED_WIRELESS:
                return getString(R.string.batt_src_wireless);
            case BatteryManager.BATTERY_PLUGGED_DOCK:
                return getString(R.string.batt_src_dock);
            default:
                return getString(R.string.batt_src_none);
        }
    }

    private String healthLabel(int health) {
        switch (health) {
            case BatteryManager.BATTERY_HEALTH_GOOD:
                return getString(R.string.batt_health_good);
            case BatteryManager.BATTERY_HEALTH_OVERHEAT:
                return getString(R.string.batt_health_overheat);
            case BatteryManager.BATTERY_HEALTH_DEAD:
                return getString(R.string.batt_health_dead);
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE:
                return getString(R.string.batt_health_over_voltage);
            case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE:
                return getString(R.string.batt_health_failure);
            case BatteryManager.BATTERY_HEALTH_COLD:
                return getString(R.string.batt_health_cold);
            default:
                return getString(R.string.batt_health_unknown);
        }
    }

    /**
     * Samsung stores the protection mode in Settings.Global: protect_battery == 0 is off,
     * 4 is the adaptive mode, other values are fixed charge limits.
     */
    private String protectionLabel() {
        int mode = Settings.Global.getInt(getContentResolver(), "protect_battery", 0);
        if (mode == 0) {
            return getString(R.string.batt_protect_off);
        }
        int threshold = Settings.Global.getInt(getContentResolver(),
                "battery_protection_threshold", 80);
        return mode == 4
                ? getString(R.string.batt_protect_adaptive, threshold)
                : getString(R.string.batt_protect_on, threshold);
    }

    private void buildCameraSection() {
        LinearLayout root = findViewById(R.id.content_root);
        root.addView(new OneUiSectionHeader(this, getString(R.string.sec_camera)));

        OneUiPreferenceGroup group = new OneUiPreferenceGroup(this);
        group.addView(toggleRow(KEY_CAMERA_LOG_VIDEO, R.string.title_camera_log_video,
                R.string.desc_camera_log_video, true));
        group.addView(toggleRow(KEY_CAMERA_4K60_LENSES, R.string.title_camera_4k60_lenses,
                R.string.desc_camera_4k60_lenses, true));
        group.addView(toggleRow(KEY_CAMERA_JPEG_100, R.string.title_camera_jpeg_100,
                R.string.desc_camera_jpeg_100, true));
        group.addView(toggleRow(KEY_CAMERA_SHUTTER_MUTE, R.string.title_camera_shutter_mute,
                R.string.desc_camera_shutter_mute, true));
        root.addView(group);
    }

    private void buildDisplaySection() {
        LinearLayout root = findViewById(R.id.content_root);
        root.addView(new OneUiSectionHeader(this, getString(R.string.sec_display)));

        OneUiPreferenceGroup switches = new OneUiPreferenceGroup(this);
        switches.addView(toggleRow(KEY_DISPLAY_OUTDOOR, R.string.title_display_outdoor,
                R.string.desc_display_outdoor, true));
        switches.addView(toggleRow(KEY_DISPLAY_SCREEN_MODE, R.string.title_display_screen_mode,
                R.string.desc_display_screen_mode, true));
        switches.addView(toggleRow(KEY_DISPLAY_SCREEN_TIMEOUT, R.string.title_display_screen_timeout,
                R.string.desc_display_screen_timeout, true));
        switches.addView(toggleRow(KEY_DISPLAY_ACTION_TIMEOUT, R.string.title_display_action_timeout,
                R.string.desc_display_action_timeout, true));
        switches.addView(toggleRow(KEY_DISPLAY_EAD, R.string.title_display_ead,
                R.string.desc_display_ead, true));
        switches.addView(toggleRow(KEY_DISPLAY_FONTS, R.string.title_display_fonts,
                R.string.desc_display_fonts, true));
        root.addView(switches);

        OneUiPreferenceGroup values = new OneUiPreferenceGroup(this);
        mScreenModeRow = new OneUiSelectRow(this)
                .title(getString(R.string.row_active_profile));
        mScreenModeRow.setOnClickListener(v -> pickScreenMode());
        values.addView(mScreenModeRow);

        mScreenTimeoutRow = new OneUiSelectRow(this)
                .title(getString(R.string.row_screen_timeout));
        mScreenTimeoutRow.setOnClickListener(v -> pickScreenTimeout());
        values.addView(mScreenTimeoutRow);

        mActionTimeoutRow = new OneUiSelectRow(this)
                .title(getString(R.string.row_action_timeout));
        mActionTimeoutRow.setOnClickListener(v -> pickActionTimeout());
        values.addView(mActionTimeoutRow);

        root.addView(values);
    }

    private void buildSystemSection() {
        LinearLayout root = findViewById(R.id.content_root);
        root.addView(new OneUiSectionHeader(this, getString(R.string.sec_system)));

        OneUiPreferenceGroup group = new OneUiPreferenceGroup(this);
        group.addView(toggleRow(KEY_SYSTEM_CALL_RECORDING, R.string.title_call_recording,
                R.string.desc_call_recording, true));
        group.addView(toggleRow(KEY_SYSTEM_WIFI_PASSWORDS, R.string.title_wifi_passwords,
                R.string.desc_wifi_passwords, true));
        group.addView(toggleRow(KEY_SYSTEM_FLAG_SECURE, R.string.title_flag_secure,
                R.string.desc_flag_secure, true));
        group.addView(toggleRow(KEY_SYSTEM_TELEMETRY, R.string.title_telemetry,
                R.string.desc_telemetry, true));
        root.addView(group);
    }

    private void addFooter() {
        LinearLayout root = findViewById(R.id.content_root);
        TextView note = new TextView(this);
        note.setText(R.string.footer_note);
        note.setTextColor(OneUiTheme.TEXT_SECONDARY);
        note.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11.5f);
        note.setLineSpacing(OneUiTheme.dp(this, 2), 1.15f);
        int padH = OneUiTheme.dp(this, 6);
        note.setPadding(padH, OneUiTheme.dp(this, 18), padH, 0);
        root.addView(note);
    }

    // ------------------------------------------------------------------ rows

    private OneUiPreferenceRow toggleRow(String key, int titleRes, int summaryRes, boolean def) {
        return new OneUiPreferenceRow(this)
                .key(key)
                .title(getString(titleRes))
                .summary(getString(summaryRes))
                .checked(mPrefs.getBoolean(key, def))
                .onToggle(this::onFeatureToggled);
    }

    private void onFeatureToggled(String key, boolean checked) {
        mPrefs.edit().putBoolean(key, checked).apply();
        mirrorToGlobal(key, checked);
        updatePermissions();
        syncDependencies();
        toast(getString(R.string.toast_saved));
    }

    /**
     * The hooks run in other processes where LSPosed remote prefs are unreliable, so every
     * toggle is mirrored into Settings.Global (WRITE_SECURE_SETTINGS is granted to this app).
     */
    private void mirrorToGlobal(String key, boolean value) {
        try {
            Settings.Global.putInt(getContentResolver(), GLOBAL_PREFIX + key, value ? 1 : 0);
        } catch (Throwable t) {
            android.util.Log.e("S23EnhancerApp", "mirror failed for " + key + ": " + t);
        }
    }

    private void syncAllPrefsToGlobal() {
        for (String key : ALL_KEYS) {
            try {
                mirrorToGlobal(key, mPrefs.getBoolean(key, defaultFor(key)));
            } catch (Throwable ignored) {
            }
        }
    }

    /** Defaults must match the ones used by the hooks (all features default to ON). */
    private boolean defaultFor(String key) {
        return true;
    }

    private void syncDependencies() {
        boolean modeOn = mPrefs.getBoolean(KEY_DISPLAY_SCREEN_MODE, true);
        boolean timeoutOn = mPrefs.getBoolean(KEY_DISPLAY_SCREEN_TIMEOUT, true);
        boolean actionOn = mPrefs.getBoolean(KEY_DISPLAY_ACTION_TIMEOUT, true);
        if (mScreenModeRow != null) mScreenModeRow.setEnabled(modeOn);
        if (mScreenTimeoutRow != null) mScreenTimeoutRow.setEnabled(timeoutOn);
        if (mActionTimeoutRow != null) mActionTimeoutRow.setEnabled(actionOn);
    }

    // ------------------------------------------------------------------ pickers

    private void pickScreenMode() {
        final CharSequence[] names = {
                getString(R.string.mode_vivid),
                getString(R.string.mode_natural),
                getString(R.string.mode_cinema),
                getString(R.string.mode_photo),
                getString(R.string.mode_basic)
        };
        int currentAuto = Settings.System.getInt(getContentResolver(),
                "screen_mode_automatic_setting", 1);
        int currentMode = Settings.System.getInt(getContentResolver(), "screen_mode_setting", 4);
        int effective = currentAuto == 1 ? 4 : currentMode;
        int selected = 0;
        for (int i = 0; i < SCREEN_MODE_VALUES.length; i++) {
            if (SCREEN_MODE_VALUES[i] == effective) {
                selected = i;
                break;
            }
        }
        final int startIndex = selected;

        mScreenModeRow.setValue(names[startIndex]);
        OneUiDialog.showSingleChoice(this, getString(R.string.title_display_screen_mode),
                names, startIndex, index -> {
                    int value = SCREEN_MODE_VALUES[index];
                    putSystemSetting("screen_mode_automatic_setting", value == 4 ? 1 : 0);
                    putSystemSetting("screen_mode_setting", value);
                    applyMdnieMode(value);
                    mScreenModeRow.setValue(names[index]);
                    toast(getString(R.string.toast_screen_mode, names[index]));
                });
    }

    private void pickScreenTimeout() {
        final CharSequence[] names = {
                getString(R.string.timeout_15s),
                getString(R.string.timeout_30s),
                getString(R.string.timeout_1m),
                getString(R.string.timeout_2m),
                getString(R.string.timeout_5m),
                getString(R.string.timeout_10m),
                getString(R.string.timeout_30m),
                getString(R.string.timeout_1h),
                getString(R.string.timeout_never)
        };
        final int startIndex = indexOfTimeout(currentTimeout());
        mScreenTimeoutRow.setValue(names[startIndex]);
        OneUiDialog.showSingleChoice(this, getString(R.string.title_display_screen_timeout),
                names, startIndex, index -> {
                    long value = SCREEN_TIMEOUT_VALUES[index];
                    putSystemSettingLong("screen_off_timeout", value);
                    mScreenTimeoutRow.setValue(names[index]);
                    toast(getString(R.string.toast_screen_timeout, names[index]));
                });
    }

    private void pickActionTimeout() {
        final CharSequence[] names = {
                getString(R.string.action_default),
                getString(R.string.action_5s),
                getString(R.string.action_10s),
                getString(R.string.action_20s),
                getString(R.string.action_30s),
                getString(R.string.action_1m),
                getString(R.string.action_2m),
                getString(R.string.action_5m),
                getString(R.string.action_custom)
        };
        int current = currentActionTimeoutMs();
        int selected = names.length - 1;
        for (int i = 0; i < ACTION_TIMEOUT_VALUES.length - 1; i++) {
            if (ACTION_TIMEOUT_VALUES[i] == current) {
                selected = i;
                break;
            }
        }
        final int startIndex = selected;
        mActionTimeoutRow.setValue(describeActionTimeout(current));

        OneUiDialog.showSingleChoice(this, getString(R.string.title_display_action_timeout),
                names, startIndex, index -> {
                    int value = ACTION_TIMEOUT_VALUES[index];
                    if (value == -1) {
                        askCustomActionTimeout(currentActionTimeoutMs());
                    } else {
                        applyActionTimeout(value);
                    }
                });
    }

    private void askCustomActionTimeout(int currentMs) {
        String initial = currentMs > 0 ? String.valueOf(currentMs / 1000) : "";
        OneUiDialog.showInput(this, getString(R.string.action_custom_title),
                getString(R.string.action_custom_hint), initial, getString(R.string.action_set),
                value -> {
                    if (value.isEmpty()) {
                        return;
                    }
                    try {
                        int seconds = Integer.parseInt(value);
                        if (seconds <= 0) {
                            toast(getString(R.string.toast_invalid_value));
                            return;
                        }
                        applyActionTimeout(seconds * 1000);
                    } catch (NumberFormatException e) {
                        toast(getString(R.string.toast_invalid_value));
                    }
                });
    }

    private void applyActionTimeout(int ms) {
        putSecureSetting("accessibility_interactive_ui_timeout_ms", String.valueOf(ms));
        putSecureSetting("accessibility_non_interactive_ui_timeout_ms", String.valueOf(ms));
        if (mActionTimeoutRow != null) {
            mActionTimeoutRow.setValue(describeActionTimeout(ms));
        }
        toast(getString(R.string.toast_action_timeout, describeActionTimeout(ms)));
    }

    // ------------------------------------------------------------------ state sync

    private void syncScreenModeFromSystem() {
        if (mScreenModeRow == null) {
            return;
        }
        int currentAuto = Settings.System.getInt(getContentResolver(),
                "screen_mode_automatic_setting", 1);
        int currentMode = Settings.System.getInt(getContentResolver(), "screen_mode_setting", 4);
        int effective = currentAuto == 1 ? 4 : currentMode;
        CharSequence[] names = {
                getString(R.string.mode_vivid),
                getString(R.string.mode_natural),
                getString(R.string.mode_cinema),
                getString(R.string.mode_photo),
                getString(R.string.mode_basic)
        };
        for (int i = 0; i < SCREEN_MODE_VALUES.length; i++) {
            if (SCREEN_MODE_VALUES[i] == effective) {
                mScreenModeRow.setValue(names[i]);
                break;
            }
        }
        syncDependencies();
    }

    private void syncScreenTimeoutFromSystem() {
        if (mScreenTimeoutRow == null) {
            return;
        }
        CharSequence[] names = {
                getString(R.string.timeout_15s),
                getString(R.string.timeout_30s),
                getString(R.string.timeout_1m),
                getString(R.string.timeout_2m),
                getString(R.string.timeout_5m),
                getString(R.string.timeout_10m),
                getString(R.string.timeout_30m),
                getString(R.string.timeout_1h),
                getString(R.string.timeout_never)
        };
        mScreenTimeoutRow.setValue(names[indexOfTimeout(currentTimeout())]);
    }

    private void syncActionTimeoutFromSystem() {
        if (mActionTimeoutRow == null) {
            return;
        }
        mActionTimeoutRow.setValue(describeActionTimeout(currentActionTimeoutMs()));
    }

    // ------------------------------------------------------------------ device card

    private void bindDeviceCard() {
        OneUiDeviceCard card = findViewById(R.id.device_card);
        String model = Build.MODEL == null ? "Galaxy S23 Ultra" : Build.MODEL;
        String vendor = Build.MANUFACTURER == null ? "Samsung" : Build.MANUFACTURER;
        if (vendor.length() > 0) {
            vendor = Character.toUpperCase(vendor.charAt(0)) + vendor.substring(1);
        }
        String details = "Android " + Build.VERSION.RELEASE + " • " + vendor;
        boolean supported = model != null && model.toUpperCase().contains("S918");
        card.bind(model, details,
                getString(supported ? R.string.status_supported : R.string.status_test_device));
        card.setStatusActive(supported);
    }

    // ------------------------------------------------------------------ system writes

    private long currentTimeout() {
        long value = Settings.System.getLong(getContentResolver(), "screen_off_timeout", 30000L);
        if (value >= Integer.MAX_VALUE - 8L || value < 0) {
            return Integer.MAX_VALUE;
        }
        return value;
    }

    private int indexOfTimeout(long timeout) {
        for (int i = 0; i < SCREEN_TIMEOUT_VALUES.length; i++) {
            if (SCREEN_TIMEOUT_VALUES[i] == timeout) {
                return i;
            }
        }
        return timeout >= Integer.MAX_VALUE - 8L ? SCREEN_TIMEOUT_VALUES.length - 1 : 1;
    }

    private int currentActionTimeoutMs() {
        return Settings.Secure.getInt(getContentResolver(),
                "accessibility_interactive_ui_timeout_ms", 0);
    }

    private String describeActionTimeout(int ms) {
        if (ms <= 0) {
            return getString(R.string.action_default);
        }
        if (ms % 60000 == 0) {
            return (ms / 60000) + " min";
        }
        return (ms / 1000) + " s";
    }

    private void putSystemSetting(String key, int value) {
        try {
            Settings.System.putInt(getContentResolver(), key, value);
        } catch (Throwable t) {
            runShell("settings put system " + key + " " + value);
        }
    }

    private void putSystemSettingLong(String key, long value) {
        try {
            Settings.System.putLong(getContentResolver(), key, value);
        } catch (Throwable t) {
            runShell("settings put system " + key + " " + value);
        }
    }

    private void putSecureSetting(String key, String value) {
        try {
            Settings.Secure.putString(getContentResolver(), key, value);
        } catch (Throwable t) {
            runShell("settings put secure " + key + " " + value);
        }
    }

    private void applyMdnieMode(int mode) {
        try {
            Object mdnie = getSystemService("mDNIe");
            if (mdnie != null) {
                Method m = mdnie.getClass().getMethod("setScreenMode", int.class);
                m.invoke(mdnie, mode);
            }
        } catch (Throwable ignored) {
        }
    }

    private void runShell(String command) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
            p.waitFor();
        } catch (Throwable ignored) {
        }
    }

    private void updatePermissions() {
        try {
            File prefsDir = new File(getApplicationInfo().dataDir, "shared_prefs");
            File prefsFile = new File(prefsDir, PrefProvider.PREF_NAME + ".xml");
            if (prefsFile.exists()) {
                prefsFile.setReadable(true, false);
            }
        } catch (Throwable ignored) {
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
