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

import com.s23.enhancements.AppUpdater;
import com.s23.enhancements.PrefProvider;
import com.s23.enhancements.R;
import com.s23.enhancements.StatusBarActions;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Single screen of the companion app. Every supported feature is one row and can be
 * switched on or off independently; the LSPosed hook reads the same keys.
 */
public class MainActivity extends Activity {

    // Camera
    public static final String KEY_CAMERA_LOG_VIDEO = "pref_camera_log_video";
    public static final String KEY_CAMERA_4K60_LENSES = "pref_camera_4k60_lenses";
    public static final String KEY_CAMERA_4K120_SLOWMO = "pref_camera_4k120_slowmo";
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
    public static final String KEY_AUTO_UPDATE = "pref_app_auto_update";

    // Battery
    public static final String KEY_BATTERY_INFO = "pref_system_battery_info";

    // Status bar
    public static final String KEY_SB_EDGE_MODE = "pref_sb_edge_space_mode";
    public static final String KEY_SB_EDGE_DP = "pref_sb_edge_space_dp";
    public static final String KEY_SB_ITEM_SPACING = "pref_sb_item_spacing";
    public static final String KEY_SB_MAX_NOTIF_ICONS = "pref_sb_max_notif_icons";
    public static final String KEY_SB_HIDDEN_ICONS = "pref_sb_hidden_icons";
    public static final String KEY_SB_ONE_CONN_ICON = "pref_sb_one_connection_icon";
    public static final String KEY_SB_BACK_BUTTON = "pref_sb_back_button";
    public static final String KEY_SB_BURN_IN = "pref_sb_burn_in_protect";

    // Status bar taps & feedback
    public static final String KEY_SB_DOUBLE_TAP_SLEEP = "pref_sb_double_tap_sleep";
    public static final String KEY_SB_TAP_VIBRATE = "pref_sb_tap_vibrate";
    public static final String KEY_SB_TAP_VIBRATE_STRENGTH = "pref_sb_tap_vibrate_strength";
    public static final String KEY_SB_TAP_BATTERY = "pref_sb_tap_battery";
    public static final String KEY_SB_LONG_BATTERY = "pref_sb_long_battery";
    public static final String KEY_SB_TAP_CLOCK = "pref_sb_tap_clock";
    public static final String KEY_SB_LONG_CLOCK = "pref_sb_long_clock";
    public static final String KEY_SB_TAP_CAMERA_HOLE = "pref_sb_tap_camera_hole";
    public static final String KEY_SB_LONG_CAMERA_HOLE = "pref_sb_long_camera_hole";
    public static final String KEY_SB_TAP_WIFI = "pref_sb_tap_wifi";
    public static final String KEY_SB_LONG_WIFI = "pref_sb_long_wifi";
    public static final String KEY_SB_TAP_MOBILE = "pref_sb_tap_mobile";
    public static final String KEY_SB_LONG_MOBILE = "pref_sb_long_mobile";

    // Notifications
    public static final String KEY_NOTIF_AUTO_EXPAND = "pref_notif_auto_expand";
    public static final String KEY_NOTIF_GROUPS_ONE_TAP = "pref_notif_expand_groups_one_tap";
    public static final String KEY_NOTIF_KEEP_SILENT_SMALL = "pref_notif_keep_silent_small";

    private static final int[] SCREEN_MODE_VALUES = {4, 3, 0, 1, 2};
    private static final long[] SCREEN_TIMEOUT_VALUES = {
            15000L, 30000L, 60000L, 120000L, 300000L, 600000L, 1800000L, 3600000L, Integer.MAX_VALUE
    };
    private static final int[] ACTION_TIMEOUT_VALUES = {
            0, 5000, 10000, 20000, 30000, 60000, 120000, 300000, -1
    };

    private static final String[][] STATUS_ICON_SLOT_GROUPS = {
            {"alarm_clock"},
            {"nfc", "nfc_on"},
            {"bluetooth", "bluetooth_connected"},
            {"volume", "zen"},
            {"dnd"},
            {"location"},
            {"ims_volte", "ims_volte2", "volte", "vowifi", "ims_vowifi"},
            {"headset"},
            {"rotate"},
            {"airplane"},
            {"hotspot"},
            {"data_saver"},
            {"vpn"},
            {"cast"},
            {"power_saver"},
            {"secure_folder"},
            {"wifi"},
            {"mobile"},
            {"clock"},
            {"battery"}
    };
    private static final int[] STATUS_ICON_LABELS = {
            R.string.sb_icon_alarm, R.string.sb_icon_nfc, R.string.sb_icon_bluetooth,
            R.string.sb_icon_volume, R.string.sb_icon_zen, R.string.sb_icon_location,
            R.string.sb_icon_ims_volte, R.string.sb_icon_headset, R.string.sb_icon_rotate,
            R.string.sb_icon_airplane, R.string.sb_icon_hotspot, R.string.sb_icon_data_saver,
            R.string.sb_icon_vpn, R.string.sb_icon_cast, R.string.sb_icon_power_saver,
            R.string.sb_icon_secure_folder, R.string.sb_icon_wifi, R.string.sb_icon_mobile,
            R.string.sb_icon_clock, R.string.sb_icon_battery
    };

    /** Mirror prefix — must match HookEntry.GLOBAL_PREFIX. */
    private static final String GLOBAL_PREFIX = "s23enh_";

    private static final String[] ALL_KEYS = {
            KEY_CAMERA_LOG_VIDEO, KEY_CAMERA_4K60_LENSES, KEY_CAMERA_4K120_SLOWMO, KEY_CAMERA_JPEG_100,
            KEY_CAMERA_SHUTTER_MUTE,
            KEY_DISPLAY_OUTDOOR, KEY_DISPLAY_SCREEN_MODE, KEY_DISPLAY_SCREEN_TIMEOUT,
            KEY_DISPLAY_ACTION_TIMEOUT, KEY_DISPLAY_EAD, KEY_DISPLAY_FONTS,
            KEY_SYSTEM_CALL_RECORDING, KEY_SYSTEM_WIFI_PASSWORDS,
            KEY_SYSTEM_FLAG_SECURE, KEY_SYSTEM_TELEMETRY,
            KEY_BATTERY_INFO,
            KEY_SB_ONE_CONN_ICON, KEY_SB_BACK_BUTTON, KEY_SB_BURN_IN,
            KEY_SB_DOUBLE_TAP_SLEEP, KEY_SB_TAP_VIBRATE,
            KEY_NOTIF_AUTO_EXPAND, KEY_NOTIF_GROUPS_ONE_TAP, KEY_NOTIF_KEEP_SILENT_SMALL
    };

    private static final String[] ALL_INT_KEYS = {
            KEY_SB_EDGE_MODE, KEY_SB_EDGE_DP, KEY_SB_ITEM_SPACING,
            KEY_SB_MAX_NOTIF_ICONS, KEY_SB_TAP_VIBRATE_STRENGTH
    };

    private static final String[] ALL_STRING_KEYS = {
            KEY_SB_HIDDEN_ICONS,
            KEY_SB_TAP_BATTERY, KEY_SB_LONG_BATTERY,
            KEY_SB_TAP_CLOCK, KEY_SB_LONG_CLOCK,
            KEY_SB_TAP_CAMERA_HOLE, KEY_SB_LONG_CAMERA_HOLE,
            KEY_SB_TAP_WIFI, KEY_SB_LONG_WIFI,
            KEY_SB_TAP_MOBILE, KEY_SB_LONG_MOBILE
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
    private OneUiSelectRow mSbEdgeSpaceRow;
    private OneUiSelectRow mSbItemSpacingRow;
    private OneUiSelectRow mSbMaxNotifRow;
    private OneUiSelectRow mSbHiddenIconsRow;
    private OneUiSelectRow mSbVibrateStrengthRow;

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
        buildStatusBarSection();
        buildStatusBarTapsSection();
        buildNotificationsSection();
        buildSystemSection();
        addFooter();

        updatePermissions();
        syncAllPrefsToGlobal();
        AppUpdater.checkOnStartup(this, mPrefs);
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
        group.addView(toggleRow(KEY_CAMERA_4K120_SLOWMO, R.string.title_camera_4k120_slowmo,
                R.string.desc_camera_4k120_slowmo, true));
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

    private void buildStatusBarSection() {
        LinearLayout root = findViewById(R.id.content_root);
        root.addView(new OneUiSectionHeader(this, getString(R.string.sec_statusbar)));

        OneUiPreferenceGroup layoutGroup = new OneUiPreferenceGroup(this);

        mSbEdgeSpaceRow = new OneUiSelectRow(this)
                .title(getString(R.string.sb_row_edge_space));
        mSbEdgeSpaceRow.setOnClickListener(v -> pickEdgeSpace());
        layoutGroup.addView(mSbEdgeSpaceRow);

        mSbItemSpacingRow = new OneUiSelectRow(this)
                .title(getString(R.string.sb_row_item_spacing));
        mSbItemSpacingRow.setOnClickListener(v -> pickItemSpacing());
        layoutGroup.addView(mSbItemSpacingRow);

        mSbMaxNotifRow = new OneUiSelectRow(this)
                .title(getString(R.string.sb_row_max_notif_icons));
        mSbMaxNotifRow.setOnClickListener(v -> pickMaxNotifIcons());
        layoutGroup.addView(mSbMaxNotifRow);

        mSbHiddenIconsRow = new OneUiSelectRow(this)
                .title(getString(R.string.sb_row_hidden_icons));
        mSbHiddenIconsRow.setOnClickListener(v -> pickHiddenIcons());
        layoutGroup.addView(mSbHiddenIconsRow);

        root.addView(layoutGroup);

        OneUiPreferenceGroup togglesGroup = new OneUiPreferenceGroup(this);
        togglesGroup.addView(toggleRow(KEY_SB_ONE_CONN_ICON, R.string.title_sb_one_conn,
                R.string.desc_sb_one_conn, false));
        togglesGroup.addView(toggleRow(KEY_SB_BACK_BUTTON, R.string.title_sb_back_btn,
                R.string.desc_sb_back_btn, false));
        togglesGroup.addView(toggleRow(KEY_SB_BURN_IN, R.string.title_sb_burn_in,
                R.string.desc_sb_burn_in, true));
        root.addView(togglesGroup);

        syncStatusBarSummaries();
    }

    private void buildStatusBarTapsSection() {
        LinearLayout root = findViewById(R.id.content_root);
        root.addView(new OneUiSectionHeader(this, getString(R.string.sec_sb_taps)));

        OneUiPreferenceGroup feedbackGroup = new OneUiPreferenceGroup(this);
        feedbackGroup.addView(toggleRow(KEY_SB_DOUBLE_TAP_SLEEP, R.string.title_sb_double_tap_sleep,
                R.string.desc_sb_double_tap_sleep, false));
        feedbackGroup.addView(toggleRow(KEY_SB_TAP_VIBRATE, R.string.title_sb_vibrate,
                R.string.desc_sb_vibrate, true));

        mSbVibrateStrengthRow = new OneUiSelectRow(this)
                .title(getString(R.string.sb_row_vibrate_strength));
        mSbVibrateStrengthRow.setOnClickListener(v -> pickVibrateStrength());
        feedbackGroup.addView(mSbVibrateStrengthRow);
        root.addView(feedbackGroup);

        OneUiPreferenceGroup actionsGroup = new OneUiPreferenceGroup(this);
        actionsGroup.addView(actionPickerRow(KEY_SB_TAP_BATTERY, R.string.sb_tap_batt));
        actionsGroup.addView(actionPickerRow(KEY_SB_LONG_BATTERY, R.string.sb_long_batt));
        actionsGroup.addView(actionPickerRow(KEY_SB_TAP_CLOCK, R.string.sb_tap_clock));
        actionsGroup.addView(actionPickerRow(KEY_SB_LONG_CLOCK, R.string.sb_long_clock));
        actionsGroup.addView(actionPickerRow(KEY_SB_TAP_CAMERA_HOLE, R.string.sb_tap_hole));
        actionsGroup.addView(actionPickerRow(KEY_SB_LONG_CAMERA_HOLE, R.string.sb_long_hole));
        actionsGroup.addView(actionPickerRow(KEY_SB_TAP_WIFI, R.string.sb_tap_wifi));
        actionsGroup.addView(actionPickerRow(KEY_SB_LONG_WIFI, R.string.sb_long_wifi));
        actionsGroup.addView(actionPickerRow(KEY_SB_TAP_MOBILE, R.string.sb_tap_mobile));
        actionsGroup.addView(actionPickerRow(KEY_SB_LONG_MOBILE, R.string.sb_long_mobile));
        root.addView(actionsGroup);

        syncVibrateStrengthSummary();
    }

    private void buildNotificationsSection() {
        LinearLayout root = findViewById(R.id.content_root);
        root.addView(new OneUiSectionHeader(this, getString(R.string.sec_notifications)));

        OneUiPreferenceGroup group = new OneUiPreferenceGroup(this);
        group.addView(toggleRow(KEY_NOTIF_AUTO_EXPAND, R.string.title_notif_auto_expand,
                R.string.desc_notif_auto_expand, false));
        group.addView(toggleRow(KEY_NOTIF_GROUPS_ONE_TAP, R.string.title_notif_groups_one_tap,
                R.string.desc_notif_groups_one_tap, false));
        group.addView(toggleRow(KEY_NOTIF_KEEP_SILENT_SMALL, R.string.title_notif_keep_silent_small,
                R.string.desc_notif_keep_silent_small, true));
        root.addView(group);
    }

    private OneUiSelectRow actionPickerRow(String prefKey, int titleRes) {
        String title = getString(titleRes);
        OneUiSelectRow row = new OneUiSelectRow(this)
                .title(title)
                .value(getString(StatusBarActions.labelResFor(
                        mPrefs.getString(prefKey, StatusBarActions.ACTION_NONE))));
        row.setOnClickListener(v -> {
            String cur = mPrefs.getString(prefKey, StatusBarActions.ACTION_NONE);
            int sel = 0;
            CharSequence[] labels = new CharSequence[StatusBarActions.ACTION_IDS.length];
            for (int i = 0; i < StatusBarActions.ACTION_IDS.length; i++) {
                String id = StatusBarActions.ACTION_IDS[i];
                labels[i] = getString(StatusBarActions.labelResFor(id));
                if (id.equals(cur)) sel = i;
            }
            OneUiDialog.showSingleChoice(
                    this,
                    title,
                    labels,
                    sel,
                    idx -> {
                        String picked = StatusBarActions.ACTION_IDS[idx];
                        mPrefs.edit().putString(prefKey, picked).apply();
                        mirrorStringToGlobal(prefKey, picked);
                        row.setValue(labels[idx]);
                        toast(getString(R.string.toast_saved));
                    },
                    getString(R.string.sb_action_test),
                    idx -> {
                        String testId = StatusBarActions.ACTION_IDS[idx];
                        StatusBarActions.performHaptic(
                                this,
                                mPrefs.getBoolean(KEY_SB_TAP_VIBRATE, true),
                                mPrefs.getInt(KEY_SB_TAP_VIBRATE_STRENGTH, 1));
                        StatusBarActions.execute(this, testId);
                    });
        });
        return row;
    }

    private void pickEdgeSpace() {
        final CharSequence[] options = {
                getString(R.string.sb_edge_samsung),
                getString(R.string.sb_edge_tight),
                getString(R.string.sb_edge_custom)
        };
        int curMode = Math.max(0, Math.min(2, mPrefs.getInt(KEY_SB_EDGE_MODE, 0)));
        OneUiDialog.showSingleChoice(this, getString(R.string.sb_row_edge_space), options, curMode, idx -> {
            if (idx == 2) {
                int curDp = mPrefs.getInt(KEY_SB_EDGE_DP, 4);
                OneUiDialog.showInput(this, getString(R.string.sb_edge_custom_title),
                        getString(R.string.sb_edge_custom_hint), String.valueOf(curDp),
                        getString(R.string.action_set), value -> {
                            try {
                                int dp = Math.max(0, Math.min(32, Integer.parseInt(value)));
                                mPrefs.edit().putInt(KEY_SB_EDGE_MODE, 2).putInt(KEY_SB_EDGE_DP, dp).apply();
                                mirrorIntToGlobal(KEY_SB_EDGE_MODE, 2);
                                mirrorIntToGlobal(KEY_SB_EDGE_DP, dp);
                                syncStatusBarSummaries();
                                toast(getString(R.string.toast_saved));
                            } catch (NumberFormatException e) {
                                toast(getString(R.string.toast_invalid_value));
                            }
                        });
            } else {
                mPrefs.edit().putInt(KEY_SB_EDGE_MODE, idx).apply();
                mirrorIntToGlobal(KEY_SB_EDGE_MODE, idx);
                syncStatusBarSummaries();
                toast(getString(R.string.toast_saved));
            }
        });
    }

    private void pickItemSpacing() {
        int cur = mPrefs.getInt(KEY_SB_ITEM_SPACING, -1);
        int start = cur < 0 ? 4 : Math.max(0, Math.min(12, cur));
        OneUiDialog.showSlider(
                this,
                getString(R.string.sb_row_item_spacing),
                getString(R.string.sb_item_spacing_sub),
                0, 12, start,
                v -> getString(R.string.sb_item_spacing_val, v),
                getString(R.string.sb_reset), -1,
                getString(R.string.sb_save),
                value -> {
                    mPrefs.edit().putInt(KEY_SB_ITEM_SPACING, value).apply();
                    mirrorIntToGlobal(KEY_SB_ITEM_SPACING, value);
                    syncStatusBarSummaries();
                    toast(getString(R.string.toast_saved));
                });
    }

    private void pickMaxNotifIcons() {
        int cur = mPrefs.getInt(KEY_SB_MAX_NOTIF_ICONS, 0);
        int start = cur <= 0 ? 3 : Math.max(1, Math.min(10, cur));
        OneUiDialog.showSlider(
                this,
                getString(R.string.sb_row_max_notif_icons),
                getString(R.string.sb_max_notif_sub),
                1, 10, start,
                v -> getString(R.string.sb_max_notif_val, v),
                getString(R.string.sb_reset), 0,
                getString(R.string.sb_save),
                value -> {
                    mPrefs.edit().putInt(KEY_SB_MAX_NOTIF_ICONS, value).apply();
                    mirrorIntToGlobal(KEY_SB_MAX_NOTIF_ICONS, value);
                    syncStatusBarSummaries();
                    toast(getString(R.string.toast_saved));
                });
    }

    private void pickHiddenIcons() {
        String raw = Settings.Secure.getString(getContentResolver(), "icon_blacklist");
        if (raw == null || raw.isEmpty()) {
            raw = mPrefs.getString(KEY_SB_HIDDEN_ICONS, "");
        }
        Set<String> active = new HashSet<>();
        if (raw != null && !raw.isEmpty() && !"null".equals(raw.trim())) {
            for (String s : raw.split(",")) {
                String t = s.trim();
                if (!t.isEmpty()) active.add(t);
            }
        }
        CharSequence[] labels = new CharSequence[STATUS_ICON_SLOT_GROUPS.length];
        boolean[] checked = new boolean[STATUS_ICON_SLOT_GROUPS.length];
        for (int i = 0; i < STATUS_ICON_SLOT_GROUPS.length; i++) {
            labels[i] = getString(STATUS_ICON_LABELS[i]);
            boolean any = false;
            for (String sl : STATUS_ICON_SLOT_GROUPS[i]) {
                if (active.contains(sl)) {
                    any = true;
                    break;
                }
            }
            checked[i] = any;
        }
        final String baseBlacklist = raw == null ? "" : raw;
        OneUiDialog.showMultiChoice(
                this,
                getString(R.string.sb_row_hidden_icons),
                labels,
                checked,
                getString(R.string.sb_save),
                res -> {
                    Set<String> managedSlots = new HashSet<>();
                    List<String> wantSlots = new ArrayList<>();
                    for (int i = 0; i < STATUS_ICON_SLOT_GROUPS.length; i++) {
                        for (String sl : STATUS_ICON_SLOT_GROUPS[i]) {
                            managedSlots.add(sl);
                            if (res[i] && !wantSlots.contains(sl)) {
                                wantSlots.add(sl);
                            }
                        }
                    }
                    List<String> merged = new ArrayList<>();
                    if (!baseBlacklist.trim().equals("null")) {
                        for (String s : baseBlacklist.split(",")) {
                            String t = s.trim();
                            if (!t.isEmpty() && !managedSlots.contains(t) && !merged.contains(t)) {
                                merged.add(t);
                            }
                        }
                    }
                    for (String sl : wantSlots) {
                        if (!merged.contains(sl)) merged.add(sl);
                    }
                    StringBuilder sb = new StringBuilder();
                    for (String sl : merged) {
                        if (sb.length() > 0) sb.append(',');
                        sb.append(sl);
                    }
                    String joined = sb.toString();
                    mPrefs.edit().putString(KEY_SB_HIDDEN_ICONS, joined).apply();
                    mirrorStringToGlobal(KEY_SB_HIDDEN_ICONS, joined);
                    putSecureSetting("icon_blacklist", joined);
                    syncStatusBarSummaries();
                    toast(getString(R.string.toast_saved));
                });
    }

    private void pickVibrateStrength() {
        final CharSequence[] options = {
                getString(R.string.sb_vib_light),
                getString(R.string.sb_vib_medium),
                getString(R.string.sb_vib_strong)
        };
        int cur = Math.max(0, Math.min(2, mPrefs.getInt(KEY_SB_TAP_VIBRATE_STRENGTH, 1)));
        OneUiDialog.showSingleChoice(this, getString(R.string.sb_row_vibrate_strength), options, cur, idx -> {
            mPrefs.edit().putInt(KEY_SB_TAP_VIBRATE_STRENGTH, idx).apply();
            mirrorIntToGlobal(KEY_SB_TAP_VIBRATE_STRENGTH, idx);
            syncVibrateStrengthSummary();
            StatusBarActions.performHaptic(this, mPrefs.getBoolean(KEY_SB_TAP_VIBRATE, true), idx);
            toast(getString(R.string.toast_saved));
        });
    }

    private void syncStatusBarSummaries() {
        if (mSbEdgeSpaceRow != null) {
            int mode = mPrefs.getInt(KEY_SB_EDGE_MODE, 0);
            if (mode == 1) {
                mSbEdgeSpaceRow.setValue(getString(R.string.sb_edge_tight));
            } else if (mode == 2) {
                mSbEdgeSpaceRow.setValue(getString(R.string.sb_edge_custom_val, mPrefs.getInt(KEY_SB_EDGE_DP, 4)));
            } else {
                mSbEdgeSpaceRow.setValue(getString(R.string.sb_edge_samsung));
            }
        }
        if (mSbItemSpacingRow != null) {
            int sp = mPrefs.getInt(KEY_SB_ITEM_SPACING, -1);
            mSbItemSpacingRow.setValue(sp < 0
                    ? getString(R.string.sb_edge_samsung)
                    : getString(R.string.sb_item_spacing_val, sp));
        }
        if (mSbMaxNotifRow != null) {
            int max = mPrefs.getInt(KEY_SB_MAX_NOTIF_ICONS, 0);
            mSbMaxNotifRow.setValue(max <= 0
                    ? getString(R.string.sb_edge_samsung)
                    : getString(R.string.sb_max_notif_val, max));
        }
        if (mSbHiddenIconsRow != null) {
            String raw = Settings.Secure.getString(getContentResolver(), "icon_blacklist");
            if (raw == null) raw = mPrefs.getString(KEY_SB_HIDDEN_ICONS, "");
            Set<String> active = new HashSet<>();
            if (raw != null && !raw.isEmpty() && !"null".equals(raw.trim())) {
                for (String s : raw.split(",")) {
                    String t = s.trim();
                    if (!t.isEmpty()) active.add(t);
                }
            }
            int count = 0;
            for (String[] grp : STATUS_ICON_SLOT_GROUPS) {
                for (String sl : grp) {
                    if (active.contains(sl)) {
                        count++;
                        break;
                    }
                }
            }
            mSbHiddenIconsRow.setValue(count == 0
                    ? getString(R.string.sb_hidden_none)
                    : getString(R.string.sb_hidden_count, count));
        }
    }

    private void syncVibrateStrengthSummary() {
        if (mSbVibrateStrengthRow == null) return;
        int cur = mPrefs.getInt(KEY_SB_TAP_VIBRATE_STRENGTH, 1);
        int res = cur == 0 ? R.string.sb_vib_light : (cur == 2 ? R.string.sb_vib_strong : R.string.sb_vib_medium);
        mSbVibrateStrengthRow.setValue(getString(res));
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

        OneUiPreferenceGroup actionsGroup = new OneUiPreferenceGroup(this);
        actionsGroup.addView(toggleRow(KEY_AUTO_UPDATE, R.string.title_auto_update,
                R.string.desc_auto_update, true));
        OneUiSelectRow checkUpdatesRow = new OneUiSelectRow(this)
                .title(getString(R.string.row_check_updates))
                .value(getString(R.string.val_version, AppUpdater.getCurrentVersion(this)));
        checkUpdatesRow.setOnClickListener(v -> AppUpdater.checkNow(this, mPrefs));
        actionsGroup.addView(checkUpdatesRow);

        OneUiSelectRow restartSysUiRow = new OneUiSelectRow(this)
                .title(getString(R.string.row_restart_systemui))
                .value(getString(R.string.val_restart_systemui));
        restartSysUiRow.setOnClickListener(v -> OneUiDialog.showConfirm(
                this,
                getString(R.string.row_restart_systemui),
                getString(R.string.confirm_restart_systemui_msg),
                getString(R.string.action_restart),
                this::restartSystemUi));
        actionsGroup.addView(restartSysUiRow);
        root.addView(actionsGroup);
    }

    private void restartSystemUi() {
        toast(getString(R.string.toast_restarting_systemui));
        try {
            Intent intent = new Intent("com.s23.enhancements.ACTION_RESTART_SYSTEMUI");
            intent.setPackage("com.android.systemui");
            sendBroadcast(intent);
        } catch (Throwable ignored) {
        }
        try {
            Settings.Global.putLong(getContentResolver(),
                    GLOBAL_PREFIX + "cmd_restart_systemui", System.currentTimeMillis());
        } catch (Throwable ignored) {
        }
        new Thread(() -> {
            try {
                Thread.sleep(400L);
                Process p = Runtime.getRuntime().exec(new String[]{
                        "su", "-c", "killall com.android.systemui 2>/dev/null || pkill -x com.android.systemui 2>/dev/null"
                });
                p.waitFor();
            } catch (Throwable ignored) {
            }
        }).start();
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

    private void mirrorIntToGlobal(String key, int value) {
        try {
            Settings.Global.putInt(getContentResolver(), GLOBAL_PREFIX + key, value);
        } catch (Throwable t) {
            android.util.Log.e("S23EnhancerApp", "mirrorInt failed for " + key + ": " + t);
        }
    }

    private void mirrorStringToGlobal(String key, String value) {
        try {
            Settings.Global.putString(getContentResolver(), GLOBAL_PREFIX + key, value == null ? "" : value);
        } catch (Throwable t) {
            android.util.Log.e("S23EnhancerApp", "mirrorString failed for " + key + ": " + t);
        }
    }

    private void syncAllPrefsToGlobal() {
        for (String key : ALL_KEYS) {
            try {
                mirrorToGlobal(key, mPrefs.getBoolean(key, defaultFor(key)));
            } catch (Throwable ignored) {
            }
        }
        for (String key : ALL_INT_KEYS) {
            try {
                mirrorIntToGlobal(key, mPrefs.getInt(key, defaultIntFor(key)));
            } catch (Throwable ignored) {
            }
        }
        for (String key : ALL_STRING_KEYS) {
            try {
                mirrorStringToGlobal(key, mPrefs.getString(key, defaultStringFor(key)));
            } catch (Throwable ignored) {
            }
        }
    }

    private boolean defaultFor(String key) {
        if (KEY_SB_ONE_CONN_ICON.equals(key)
                || KEY_SB_BACK_BUTTON.equals(key)
                || KEY_SB_DOUBLE_TAP_SLEEP.equals(key)
                || KEY_NOTIF_AUTO_EXPAND.equals(key)
                || KEY_NOTIF_GROUPS_ONE_TAP.equals(key)) {
            return false;
        }
        return true;
    }

    private int defaultIntFor(String key) {
        if (KEY_SB_EDGE_DP.equals(key)) return 4;
        if (KEY_SB_ITEM_SPACING.equals(key)) return -1;
        if (KEY_SB_TAP_VIBRATE_STRENGTH.equals(key)) return 1;
        return 0;
    }

    private String defaultStringFor(String key) {
        if (KEY_SB_HIDDEN_ICONS.equals(key)) return "";
        return StatusBarActions.ACTION_NONE;
    }

    private void syncDependencies() {
        boolean modeOn = mPrefs.getBoolean(KEY_DISPLAY_SCREEN_MODE, true);
        boolean timeoutOn = mPrefs.getBoolean(KEY_DISPLAY_SCREEN_TIMEOUT, true);
        boolean actionOn = mPrefs.getBoolean(KEY_DISPLAY_ACTION_TIMEOUT, true);
        boolean vibOn = mPrefs.getBoolean(KEY_SB_TAP_VIBRATE, true);
        if (mScreenModeRow != null) mScreenModeRow.setEnabled(modeOn);
        if (mScreenTimeoutRow != null) mScreenTimeoutRow.setEnabled(timeoutOn);
        if (mActionTimeoutRow != null) mActionTimeoutRow.setEnabled(actionOn);
        if (mSbVibrateStrengthRow != null) mSbVibrateStrengthRow.setEnabled(vibOn);
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
