package com.s23.enhancements.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.s23.enhancements.AppUpdater;
import com.s23.enhancements.R;
import com.s23.enhancements.StatusBarActions;
import com.s23.enhancements.ui.oneui.OneUiDialog;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Companion app main screen built with SESL9 (One UI 9) AppBarLayout,
 * CollapsingToolbarLayout, and PreferenceFragmentCompat.
 */
public class MainActivity extends AppCompatActivity {

    public static final String PREF_NAME = "s23_preferences";

    // Camera
    public static final String KEY_CAMERA_LOG_VIDEO = "pref_camera_log_video";
    public static final String KEY_CAMERA_4K60_LENSES = "pref_camera_4k60_lenses";
    public static final String KEY_CAMERA_4K120_SLOWMO = "pref_camera_4k120_slowmo";
    public static final String KEY_CAMERA_JPEG_100 = "pref_camera_jpeg_100";
    public static final String KEY_CAMERA_SHUTTER_MUTE = "pref_camera_shutter_mute";
    public static final String KEY_CAMERA_SKIP_POSTPROC = "pref_camera_skip_postproc";

    // Display
    public static final String KEY_DISPLAY_OUTDOOR = "pref_display_outdoor_mode";
    public static final String KEY_DISPLAY_SCREEN_MODE = "pref_display_screen_mode";
    public static final String KEY_DISPLAY_SCREEN_TIMEOUT = "pref_display_screen_timeout";
    public static final String KEY_DISPLAY_ACTION_TIMEOUT = "pref_display_action_timeout";
    public static final String KEY_DISPLAY_EAD = "pref_display_adaptive_color_tone";
    public static final String KEY_DISPLAY_FONTS = "pref_display_unlock_fonts";

    // System & Updates
    public static final String KEY_SYSTEM_CALL_RECORDING = "pref_telephony_call_recording";
    public static final String KEY_SYSTEM_WIFI_PASSWORDS = "pref_system_show_wifi_passwords";
    public static final String KEY_SYSTEM_FLAG_SECURE = "pref_system_disable_flag_secure";
    public static final String KEY_SYSTEM_TELEMETRY = "pref_system_disable_telemetry";
    public static final String KEY_SYSTEM_APP_INFO_DETAILS = "pref_system_app_info_details";
    public static final String KEY_SYSTEM_FP_IMPROVE_ACCURACY = "pref_system_fp_improve_accuracy";
    public static final String KEY_AUTO_UPDATE = "pref_app_auto_update";

    // Battery
    public static final String KEY_BATTERY_INFO = "pref_system_battery_info";

    // Lock screen
    public static final String KEY_LOCKSCREEN_WIDGET_ROWS = "pref_lockscreen_widget_rows";
    public static final String KEY_LOCKSCREEN_CHARGING_INFO = "pref_lockscreen_charging_info";

    // Recents
    public static final String KEY_RECENTS_FORCE_STOP = "pref_recents_force_stop";
    public static final String KEY_RECENTS_RESTART = "pref_recents_restart";
    public static final String KEY_RECENTS_CLEAR_CACHE = "pref_recents_clear_cache";
    public static final String KEY_RECENTS_SCREENSHOT = "pref_recents_screenshot";

    // Status bar
    public static final String KEY_SB_EDGE_MODE = "pref_sb_edge_space_mode";
    public static final String KEY_SB_EDGE_DP = "pref_sb_edge_space_dp";
    public static final String KEY_SB_ITEM_SPACING = "pref_sb_item_spacing";
    public static final String KEY_SB_MAX_NOTIF_ICONS = "pref_sb_max_notif_icons";
    public static final String KEY_SB_HIDDEN_ICONS = "pref_sb_hidden_icons";
    public static final String KEY_SB_ONE_CONN_ICON = "pref_sb_one_connection_icon";
    public static final String KEY_SB_BACK_BUTTON = "pref_sb_back_button";
    public static final String KEY_SB_HIDE_BATTERY_PCT = "pref_sb_hide_battery_pct";
    public static final String KEY_SB_BURN_IN = "pref_sb_burn_in_protect";
    public static final String KEY_SB_NET_SPEED = "pref_sb_net_speed";
    public static final String KEY_SB_NET_SPEED_MODE = "pref_sb_net_speed_mode";
    public static final String KEY_SB_NET_SPEED_AUTO_HIDE = "pref_sb_net_speed_auto_hide";

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

    private static final String GLOBAL_PREFIX = "s23enh_";

    private static final String[] ALL_KEYS = {
            KEY_CAMERA_LOG_VIDEO, KEY_CAMERA_4K60_LENSES, KEY_CAMERA_4K120_SLOWMO, KEY_CAMERA_JPEG_100,
            KEY_CAMERA_SHUTTER_MUTE, KEY_CAMERA_SKIP_POSTPROC,
            KEY_DISPLAY_OUTDOOR, KEY_DISPLAY_SCREEN_MODE, KEY_DISPLAY_SCREEN_TIMEOUT,
            KEY_DISPLAY_ACTION_TIMEOUT, KEY_DISPLAY_EAD, KEY_DISPLAY_FONTS,
            KEY_LOCKSCREEN_CHARGING_INFO,
            KEY_RECENTS_FORCE_STOP, KEY_RECENTS_RESTART, KEY_RECENTS_CLEAR_CACHE, KEY_RECENTS_SCREENSHOT,
            KEY_SYSTEM_CALL_RECORDING, KEY_SYSTEM_WIFI_PASSWORDS,
            KEY_SYSTEM_FLAG_SECURE, KEY_SYSTEM_TELEMETRY, KEY_SYSTEM_APP_INFO_DETAILS,
            KEY_SYSTEM_FP_IMPROVE_ACCURACY,
            KEY_BATTERY_INFO,
            KEY_SB_ONE_CONN_ICON, KEY_SB_BACK_BUTTON, KEY_SB_BURN_IN,
            KEY_SB_NET_SPEED, KEY_SB_NET_SPEED_AUTO_HIDE,
            KEY_SB_DOUBLE_TAP_SLEEP, KEY_SB_TAP_VIBRATE,
            KEY_SB_HIDE_BATTERY_PCT,
            KEY_NOTIF_AUTO_EXPAND, KEY_NOTIF_GROUPS_ONE_TAP, KEY_NOTIF_KEEP_SILENT_SMALL
    };

    private static final String[] ALL_INT_KEYS = {
            KEY_LOCKSCREEN_WIDGET_ROWS,
            KEY_SB_EDGE_MODE, KEY_SB_EDGE_DP, KEY_SB_ITEM_SPACING,
            KEY_SB_MAX_NOTIF_ICONS, KEY_SB_NET_SPEED_MODE, KEY_SB_TAP_VIBRATE_STRENGTH
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        CollapsingToolbarLayout collapsing = findViewById(R.id.collapsing_toolbar);
        if (collapsing != null) {
            collapsing.setTitle(getString(R.string.app_name));
            collapsing.seslSetSubtitle(getString(R.string.app_subtitle));
        }

        mPrefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings_container, new SettingsFragment())
                    .commit();
        }

        syncAllPrefsToGlobal();
        AppUpdater.checkOnStartup(this, mPrefs);
    }

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

    private static boolean defaultFor(String key) {
        if (KEY_CAMERA_SKIP_POSTPROC.equals(key)
                || KEY_SB_ONE_CONN_ICON.equals(key)
                || KEY_SB_BACK_BUTTON.equals(key)
                || KEY_SB_NET_SPEED.equals(key)
                || KEY_SB_DOUBLE_TAP_SLEEP.equals(key)
                || KEY_SB_HIDE_BATTERY_PCT.equals(key)
                || KEY_NOTIF_AUTO_EXPAND.equals(key)
                || KEY_NOTIF_GROUPS_ONE_TAP.equals(key)) {
            return false;
        }
        return true;
    }

    private static int defaultIntFor(String key) {
        if (KEY_SB_EDGE_DP.equals(key)) return 4;
        if (KEY_SB_ITEM_SPACING.equals(key)) return -1;
        if (KEY_SB_TAP_VIBRATE_STRENGTH.equals(key)) return 1;
        return 0;
    }

    private static String defaultStringFor(String key) {
        if (KEY_SB_HIDDEN_ICONS.equals(key)) return "";
        return StatusBarActions.ACTION_NONE;
    }

    // ------------------------------------------------------------------ SESL9 PreferenceFragmentCompat

    public static class SettingsFragment extends PreferenceFragmentCompat {

        private Preference mBattLevelRow;
        private Preference mBattStatusRow;
        private Preference mBattSourceRow;
        private Preference mBattRemainingRow;
        private Preference mBattHealthRow;
        private Preference mBattTempRow;
        private Preference mBattVoltageRow;
        private Preference mBattCurrentRow;
        private Preference mBattPowerRow;
        private Preference mBattChargeRow;
        private Preference mBattTechRow;
        private Preference mBattProtectRow;
        private boolean mBatteryReceiverRegistered;

        private Preference mScreenModeRow;
        private Preference mScreenTimeoutRow;
        private Preference mActionTimeoutRow;
        private Preference mLockscreenWidgetRowsRow;
        private Preference mSbEdgeSpaceRow;
        private Preference mSbItemSpacingRow;
        private Preference mSbMaxNotifRow;
        private Preference mSbHiddenIconsRow;
        private Preference mSbNetSpeedModeRow;
        private SwitchPreferenceCompat mSbNetSpeedAutoHideSwitch;
        private Preference mSbVibrateStrengthRow;

        private final BroadcastReceiver mBatteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
                    updateBattery(intent);
                }
            }
        };

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            Context ctx = requireContext();
            getPreferenceManager().setSharedPreferencesName(PREF_NAME);
            PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(ctx);
            setPreferenceScreen(screen);

            addDeviceSection(screen, ctx);
            addBatterySection(screen, ctx);
            addCameraSection(screen, ctx);
            addDisplaySection(screen, ctx);
            addLockScreenSection(screen, ctx);
            addRecentsSection(screen, ctx);
            addStatusBarSection(screen, ctx);
            addStatusBarTapsSection(screen, ctx);
            addNotificationsSection(screen, ctx);
            addSystemSection(screen, ctx);
        }

        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            if (getListView() != null) {
                getListView().setClipToPadding(false);
            }
        }

        @Override
        public void onResume() {
            super.onResume();
            registerBatteryReceiver();
            syncScreenModeFromSystem();
            syncScreenTimeoutFromSystem();
            syncActionTimeoutFromSystem();
            syncLockScreenSummary();
            syncStatusBarSummaries();
            syncVibrateStrengthSummary();
            syncDependencies();
        }

        @Override
        public void onPause() {
            super.onPause();
            if (mBatteryReceiverRegistered && getActivity() != null) {
                try {
                    getActivity().unregisterReceiver(mBatteryReceiver);
                } catch (Throwable ignored) {
                }
                mBatteryReceiverRegistered = false;
            }
        }

        private MainActivity host() {
            return (MainActivity) requireActivity();
        }

        private SharedPreferences prefs() {
            return host().mPrefs;
        }

        private void toast(String msg) {
            if (getActivity() != null) {
                Toast.makeText(getActivity(), msg, Toast.LENGTH_SHORT).show();
            }
        }

        private PreferenceCategory category(PreferenceScreen screen, Context ctx, int titleRes) {
            PreferenceCategory cat = new PreferenceCategory(ctx);
            cat.setTitle(titleRes);
            screen.addPreference(cat);
            return cat;
        }

        private Preference infoPref(PreferenceCategory cat, Context ctx, int titleRes) {
            Preference p = new Preference(ctx);
            p.setTitle(titleRes);
            p.setPersistent(false);
            p.setSelectable(false);
            p.setIconSpaceReserved(false);
            cat.addPreference(p);
            return p;
        }

        private Preference actionPref(PreferenceCategory cat, Context ctx, int titleRes) {
            Preference p = new Preference(ctx);
            p.setTitle(titleRes);
            p.setPersistent(false);
            p.setIconSpaceReserved(false);
            cat.addPreference(p);
            return p;
        }

        private SwitchPreferenceCompat switchPref(PreferenceCategory cat, Context ctx,
                                                  String key, int titleRes, int summaryRes, boolean def) {
            SwitchPreferenceCompat sw = new SwitchPreferenceCompat(ctx);
            sw.setKey(key);
            sw.setTitle(titleRes);
            sw.setSummary(summaryRes);
            sw.setIconSpaceReserved(false);
            sw.setPersistent(false);
            sw.setChecked(prefs().getBoolean(key, def));
            sw.setOnPreferenceChangeListener((pref, newVal) -> {
                boolean checked = Boolean.TRUE.equals(newVal);
                prefs().edit().putBoolean(key, checked).apply();
                host().mirrorToGlobal(key, checked);
                syncDependencies();
                toast(getString(R.string.toast_saved));
                return true;
            });
            cat.addPreference(sw);
            return sw;
        }

        // ---------------------------------------------------------- sections

        private void addDeviceSection(PreferenceScreen screen, Context ctx) {
            String model = Build.MODEL == null ? "Galaxy S23 Ultra" : Build.MODEL;
            String vendor = Build.MANUFACTURER == null ? "Samsung" : Build.MANUFACTURER;
            if (vendor.length() > 0) {
                vendor = Character.toUpperCase(vendor.charAt(0)) + vendor.substring(1);
            }
            boolean supported = model.toUpperCase(Locale.ROOT).contains("S918");
            String status = getString(supported ? R.string.status_supported : R.string.status_test_device);

            PreferenceCategory cat = new PreferenceCategory(ctx);
            screen.addPreference(cat);

            Preference device = new Preference(ctx);
            device.setTitle(model);
            device.setSummary("Android " + Build.VERSION.RELEASE + " • " + vendor + " • " + status);
            device.setPersistent(false);
            device.setSelectable(false);
            device.setIconSpaceReserved(false);
            cat.addPreference(device);
        }

        private void addBatterySection(PreferenceScreen screen, Context ctx) {
            PreferenceCategory cat = category(screen, ctx, R.string.sec_battery);
            mBattLevelRow = infoPref(cat, ctx, R.string.batt_level);
            mBattStatusRow = infoPref(cat, ctx, R.string.batt_status);
            mBattSourceRow = infoPref(cat, ctx, R.string.batt_source);
            mBattRemainingRow = infoPref(cat, ctx, R.string.batt_remaining);
            mBattHealthRow = infoPref(cat, ctx, R.string.batt_health);
            mBattTempRow = infoPref(cat, ctx, R.string.batt_temp);
            mBattVoltageRow = infoPref(cat, ctx, R.string.batt_voltage);
            mBattCurrentRow = infoPref(cat, ctx, R.string.batt_current);
            mBattPowerRow = infoPref(cat, ctx, R.string.batt_power);
            mBattChargeRow = infoPref(cat, ctx, R.string.batt_charge);
            mBattTechRow = infoPref(cat, ctx, R.string.batt_tech);
            mBattProtectRow = infoPref(cat, ctx, R.string.batt_protect);
            switchPref(cat, ctx, KEY_BATTERY_INFO, R.string.title_battery_info, R.string.desc_battery_info, true);
            updateBattery(null);
        }

        private void addCameraSection(PreferenceScreen screen, Context ctx) {
            PreferenceCategory cat = category(screen, ctx, R.string.sec_camera);
            switchPref(cat, ctx, KEY_CAMERA_LOG_VIDEO, R.string.title_camera_log_video, R.string.desc_camera_log_video, true);
            switchPref(cat, ctx, KEY_CAMERA_4K60_LENSES, R.string.title_camera_4k60_lenses, R.string.desc_camera_4k60_lenses, true);
            switchPref(cat, ctx, KEY_CAMERA_4K120_SLOWMO, R.string.title_camera_4k120_slowmo, R.string.desc_camera_4k120_slowmo, true);
            switchPref(cat, ctx, KEY_CAMERA_JPEG_100, R.string.title_camera_jpeg_100, R.string.desc_camera_jpeg_100, true);
            switchPref(cat, ctx, KEY_CAMERA_SHUTTER_MUTE, R.string.title_camera_shutter_mute, R.string.desc_camera_shutter_mute, true);
            switchPref(cat, ctx, KEY_CAMERA_SKIP_POSTPROC, R.string.title_camera_skip_postproc, R.string.desc_camera_skip_postproc, false);
        }

        private void addDisplaySection(PreferenceScreen screen, Context ctx) {
            PreferenceCategory cat = category(screen, ctx, R.string.sec_display);
            switchPref(cat, ctx, KEY_DISPLAY_OUTDOOR, R.string.title_display_outdoor, R.string.desc_display_outdoor, true);
            switchPref(cat, ctx, KEY_DISPLAY_SCREEN_MODE, R.string.title_display_screen_mode, R.string.desc_display_screen_mode, true);
            mScreenModeRow = actionPref(cat, ctx, R.string.row_active_profile);
            mScreenModeRow.setOnPreferenceClickListener(p -> {
                pickScreenMode();
                return true;
            });

            switchPref(cat, ctx, KEY_DISPLAY_SCREEN_TIMEOUT, R.string.title_display_screen_timeout, R.string.desc_display_screen_timeout, true);
            mScreenTimeoutRow = actionPref(cat, ctx, R.string.row_screen_timeout);
            mScreenTimeoutRow.setOnPreferenceClickListener(p -> {
                pickScreenTimeout();
                return true;
            });

            switchPref(cat, ctx, KEY_DISPLAY_ACTION_TIMEOUT, R.string.title_display_action_timeout, R.string.desc_display_action_timeout, true);
            mActionTimeoutRow = actionPref(cat, ctx, R.string.row_action_timeout);
            mActionTimeoutRow.setOnPreferenceClickListener(p -> {
                pickActionTimeout();
                return true;
            });

            switchPref(cat, ctx, KEY_DISPLAY_EAD, R.string.title_display_ead, R.string.desc_display_ead, true);
            switchPref(cat, ctx, KEY_DISPLAY_FONTS, R.string.title_display_fonts, R.string.desc_display_fonts, true);
        }

        private void addLockScreenSection(PreferenceScreen screen, Context ctx) {
            PreferenceCategory cat = category(screen, ctx, R.string.sec_lockscreen);
            mLockscreenWidgetRowsRow = actionPref(cat, ctx, R.string.row_lockscreen_widget_rows);
            mLockscreenWidgetRowsRow.setOnPreferenceClickListener(p -> {
                pickLockscreenWidgetRows();
                return true;
            });
            syncLockScreenSummary();
            switchPref(cat, ctx, KEY_LOCKSCREEN_CHARGING_INFO, R.string.title_ls_charging_info,
                    R.string.desc_ls_charging_info, true);
        }

        private void addRecentsSection(PreferenceScreen screen, Context ctx) {
            PreferenceCategory cat = category(screen, ctx, R.string.sec_recents);
            switchPref(cat, ctx, KEY_RECENTS_FORCE_STOP, R.string.title_recents_force_stop,
                    R.string.desc_recents_force_stop, true);
            switchPref(cat, ctx, KEY_RECENTS_RESTART, R.string.title_recents_restart,
                    R.string.desc_recents_restart, true);
            switchPref(cat, ctx, KEY_RECENTS_CLEAR_CACHE, R.string.title_recents_clear_cache,
                    R.string.desc_recents_clear_cache, true);
            switchPref(cat, ctx, KEY_RECENTS_SCREENSHOT, R.string.title_recents_screenshot,
                    R.string.desc_recents_screenshot, true);
        }

        private void pickLockscreenWidgetRows() {
            final CharSequence[] labels = {
                    getString(R.string.ls_rows_default),
                    getString(R.string.ls_rows_2),
                    getString(R.string.ls_rows_3)
            };
            final int[] values = {0, 2, 3};
            int cur = prefs().getInt(KEY_LOCKSCREEN_WIDGET_ROWS, 0);
            int checked = cur == 2 ? 1 : (cur == 3 ? 2 : 0);
            OneUiDialog.showSingleChoice(
                    host(),
                    getString(R.string.row_lockscreen_widget_rows),
                    labels,
                    checked,
                    which -> {
                        int val = values[which];
                        prefs().edit().putInt(KEY_LOCKSCREEN_WIDGET_ROWS, val).apply();
                        host().mirrorIntToGlobal(KEY_LOCKSCREEN_WIDGET_ROWS, val);
                        syncLockScreenSummary();
                        toast(getString(R.string.toast_saved));
                    });
        }

        private void syncLockScreenSummary() {
            if (mLockscreenWidgetRowsRow == null || getActivity() == null) return;
            int v = prefs().getInt(KEY_LOCKSCREEN_WIDGET_ROWS, 0);
            int res = v == 2 ? R.string.ls_rows_2 : (v == 3 ? R.string.ls_rows_3 : R.string.ls_rows_default);
            mLockscreenWidgetRowsRow.setSummary(res);
        }

        private void addStatusBarSection(PreferenceScreen screen, Context ctx) {
            PreferenceCategory cat = category(screen, ctx, R.string.sec_statusbar);

            mSbEdgeSpaceRow = actionPref(cat, ctx, R.string.sb_row_edge_space);
            mSbEdgeSpaceRow.setOnPreferenceClickListener(p -> {
                pickEdgeSpace();
                return true;
            });

            mSbItemSpacingRow = actionPref(cat, ctx, R.string.sb_row_item_spacing);
            mSbItemSpacingRow.setOnPreferenceClickListener(p -> {
                pickItemSpacing();
                return true;
            });

            mSbMaxNotifRow = actionPref(cat, ctx, R.string.sb_row_max_notif_icons);
            mSbMaxNotifRow.setOnPreferenceClickListener(p -> {
                pickMaxNotifIcons();
                return true;
            });

            mSbHiddenIconsRow = actionPref(cat, ctx, R.string.sb_row_hidden_icons);
            mSbHiddenIconsRow.setOnPreferenceClickListener(p -> {
                pickHiddenIcons();
                return true;
            });

            switchPref(cat, ctx, KEY_SB_ONE_CONN_ICON, R.string.title_sb_one_conn, R.string.desc_sb_one_conn, false);
            switchPref(cat, ctx, KEY_SB_BACK_BUTTON, R.string.title_sb_back_btn, R.string.desc_sb_back_btn, false);
            switchPref(cat, ctx, KEY_SB_HIDE_BATTERY_PCT, R.string.title_sb_hide_battery_pct, R.string.desc_sb_hide_battery_pct, false);
            switchPref(cat, ctx, KEY_SB_BURN_IN, R.string.title_sb_burn_in, R.string.desc_sb_burn_in, true);
            switchPref(cat, ctx, KEY_SB_NET_SPEED, R.string.title_sb_net_speed, R.string.desc_sb_net_speed, false);

            mSbNetSpeedModeRow = actionPref(cat, ctx, R.string.sb_row_net_speed_mode);
            mSbNetSpeedModeRow.setOnPreferenceClickListener(p -> {
                pickNetSpeedMode();
                return true;
            });

            mSbNetSpeedAutoHideSwitch = switchPref(
                    cat, ctx, KEY_SB_NET_SPEED_AUTO_HIDE,
                    R.string.title_sb_net_speed_auto_hide, R.string.desc_sb_net_speed_auto_hide, true);
        }

        private void addStatusBarTapsSection(PreferenceScreen screen, Context ctx) {
            PreferenceCategory cat = category(screen, ctx, R.string.sec_sb_taps);

            switchPref(cat, ctx, KEY_SB_DOUBLE_TAP_SLEEP, R.string.title_sb_double_tap_sleep, R.string.desc_sb_double_tap_sleep, false);
            switchPref(cat, ctx, KEY_SB_TAP_VIBRATE, R.string.title_sb_vibrate, R.string.desc_sb_vibrate, true);

            mSbVibrateStrengthRow = actionPref(cat, ctx, R.string.sb_row_vibrate_strength);
            mSbVibrateStrengthRow.setOnPreferenceClickListener(p -> {
                pickVibrateStrength();
                return true;
            });

            addActionPickerRow(cat, ctx, KEY_SB_TAP_BATTERY, R.string.sb_tap_batt);
            addActionPickerRow(cat, ctx, KEY_SB_LONG_BATTERY, R.string.sb_long_batt);
            addActionPickerRow(cat, ctx, KEY_SB_TAP_CLOCK, R.string.sb_tap_clock);
            addActionPickerRow(cat, ctx, KEY_SB_LONG_CLOCK, R.string.sb_long_clock);
            addActionPickerRow(cat, ctx, KEY_SB_TAP_CAMERA_HOLE, R.string.sb_tap_hole);
            addActionPickerRow(cat, ctx, KEY_SB_LONG_CAMERA_HOLE, R.string.sb_long_hole);
            addActionPickerRow(cat, ctx, KEY_SB_TAP_WIFI, R.string.sb_tap_wifi);
            addActionPickerRow(cat, ctx, KEY_SB_LONG_WIFI, R.string.sb_long_wifi);
            addActionPickerRow(cat, ctx, KEY_SB_TAP_MOBILE, R.string.sb_tap_mobile);
            addActionPickerRow(cat, ctx, KEY_SB_LONG_MOBILE, R.string.sb_long_mobile);
        }

        private void addNotificationsSection(PreferenceScreen screen, Context ctx) {
            PreferenceCategory cat = category(screen, ctx, R.string.sec_notifications);
            switchPref(cat, ctx, KEY_NOTIF_AUTO_EXPAND, R.string.title_notif_auto_expand, R.string.desc_notif_auto_expand, false);
            switchPref(cat, ctx, KEY_NOTIF_GROUPS_ONE_TAP, R.string.title_notif_groups_one_tap, R.string.desc_notif_groups_one_tap, false);
            switchPref(cat, ctx, KEY_NOTIF_KEEP_SILENT_SMALL, R.string.title_notif_keep_silent_small, R.string.desc_notif_keep_silent_small, true);
        }

        private void addSystemSection(PreferenceScreen screen, Context ctx) {
            PreferenceCategory cat = category(screen, ctx, R.string.sec_system);
            switchPref(cat, ctx, KEY_SYSTEM_CALL_RECORDING, R.string.title_call_recording, R.string.desc_call_recording, true);
            switchPref(cat, ctx, KEY_SYSTEM_WIFI_PASSWORDS, R.string.title_wifi_passwords, R.string.desc_wifi_passwords, true);
            switchPref(cat, ctx, KEY_SYSTEM_FLAG_SECURE, R.string.title_flag_secure, R.string.desc_flag_secure, true);
            switchPref(cat, ctx, KEY_SYSTEM_TELEMETRY, R.string.title_telemetry, R.string.desc_telemetry, true);
            switchPref(cat, ctx, KEY_SYSTEM_APP_INFO_DETAILS, R.string.title_app_info_details, R.string.desc_app_info_details, true);
            switchPref(cat, ctx, KEY_SYSTEM_FP_IMPROVE_ACCURACY, R.string.title_fp_improve_accuracy, R.string.desc_fp_improve_accuracy, true);

            switchPref(cat, ctx, KEY_AUTO_UPDATE, R.string.title_auto_update, R.string.desc_auto_update, true);

            Preference checkUpdates = actionPref(cat, ctx, R.string.row_check_updates);
            checkUpdates.setSummary(getString(R.string.val_version, AppUpdater.getCurrentVersion(ctx)));
            checkUpdates.setOnPreferenceClickListener(p -> {
                AppUpdater.checkNow(host(), prefs());
                return true;
            });

            Preference restartSysUi = actionPref(cat, ctx, R.string.row_restart_systemui);
            restartSysUi.setSummary(R.string.val_restart_systemui);
            restartSysUi.setOnPreferenceClickListener(p -> {
                OneUiDialog.showConfirm(
                        host(),
                        getString(R.string.row_restart_systemui),
                        getString(R.string.confirm_restart_systemui_msg),
                        getString(R.string.action_restart),
                        this::restartSystemUi);
                return true;
            });

            Preference footer = new Preference(ctx);
            footer.setSummary(R.string.footer_note);
            footer.setPersistent(false);
            footer.setSelectable(false);
            footer.setIconSpaceReserved(false);
            screen.addPreference(footer);
        }

        // ---------------------------------------------------------- action pickers

        private void addActionPickerRow(PreferenceCategory cat, Context ctx, String prefKey, int titleRes) {
            String title = getString(titleRes);
            Preference row = actionPref(cat, ctx, titleRes);
            row.setSummary(StatusBarActions.formatLabel(ctx, prefs().getString(prefKey, StatusBarActions.ACTION_NONE)));
            row.setOnPreferenceClickListener(p -> {
                String cur = prefs().getString(prefKey, StatusBarActions.ACTION_NONE);
                String matchId = (cur != null && cur.startsWith(StatusBarActions.ACTION_APP_PREFIX))
                        ? StatusBarActions.ACTION_APP
                        : cur;
                int sel = 0;
                CharSequence[] labels = new CharSequence[StatusBarActions.ACTION_IDS.length];
                for (int i = 0; i < StatusBarActions.ACTION_IDS.length; i++) {
                    String id = StatusBarActions.ACTION_IDS[i];
                    labels[i] = getString(StatusBarActions.labelResFor(id));
                    if (id.equals(matchId)) sel = i;
                }
                OneUiDialog.showSingleChoice(
                        host(),
                        title,
                        labels,
                        sel,
                        idx -> {
                            String picked = StatusBarActions.ACTION_IDS[idx];
                            if (StatusBarActions.ACTION_APP.equals(picked)) {
                                pickAppForAction(prefKey, row);
                                return;
                            }
                            prefs().edit().putString(prefKey, picked).apply();
                            host().mirrorStringToGlobal(prefKey, picked);
                            row.setSummary(labels[idx]);
                            toast(getString(R.string.toast_saved));
                        },
                        getString(R.string.sb_action_test),
                        idx -> {
                            String testId = StatusBarActions.ACTION_IDS[idx];
                            if (StatusBarActions.ACTION_APP.equals(testId)) {
                                String saved = prefs().getString(prefKey, StatusBarActions.ACTION_NONE);
                                if (saved != null && saved.startsWith(StatusBarActions.ACTION_APP_PREFIX)) {
                                    StatusBarActions.performHaptic(
                                            host(),
                                            prefs().getBoolean(KEY_SB_TAP_VIBRATE, true),
                                            prefs().getInt(KEY_SB_TAP_VIBRATE_STRENGTH, 1));
                                    StatusBarActions.execute(host(), saved);
                                } else {
                                    pickAppForAction(prefKey, row);
                                }
                                return;
                            }
                            StatusBarActions.performHaptic(
                                    host(),
                                    prefs().getBoolean(KEY_SB_TAP_VIBRATE, true),
                                    prefs().getInt(KEY_SB_TAP_VIBRATE_STRENGTH, 1));
                            StatusBarActions.execute(host(), testId);
                        });
                return true;
            });
        }

        private void pickAppForAction(String prefKey, Preference row) {
            Context ctx = host();
            PackageManager pm = ctx.getPackageManager();
            Intent mainIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> resolved = pm.queryIntentActivities(mainIntent, 0);
            Map<String, String> byPkg = new LinkedHashMap<>();
            if (resolved != null) {
                for (ResolveInfo ri : resolved) {
                    if (ri == null || ri.activityInfo == null || ri.activityInfo.packageName == null) continue;
                    String pkg = ri.activityInfo.packageName;
                    if (byPkg.containsKey(pkg)) continue;
                    CharSequence labelCs = ri.loadLabel(pm);
                    String label = labelCs != null ? labelCs.toString().trim() : pkg;
                    byPkg.put(pkg, label.isEmpty() ? pkg : label);
                }
            }
            if (byPkg.isEmpty()) {
                toast(getString(R.string.sb_act_app_none_found));
                return;
            }

            List<Map.Entry<String, String>> entries = new ArrayList<>(byPkg.entrySet());
            Collections.sort(entries, (a, b) -> {
                int c = a.getValue().compareToIgnoreCase(b.getValue());
                return c != 0 ? c : a.getKey().compareTo(b.getKey());
            });

            String cur = prefs().getString(prefKey, StatusBarActions.ACTION_NONE);
            String curPkg = (cur != null && cur.startsWith(StatusBarActions.ACTION_APP_PREFIX))
                    ? cur.substring(StatusBarActions.ACTION_APP_PREFIX.length())
                    : null;
            int sel = 0;
            CharSequence[] labels = new CharSequence[entries.size()];
            String[] pkgs = new String[entries.size()];
            for (int i = 0; i < entries.size(); i++) {
                pkgs[i] = entries.get(i).getKey();
                labels[i] = entries.get(i).getValue();
                if (pkgs[i].equals(curPkg)) sel = i;
            }

            OneUiDialog.showSingleChoice(
                    host(),
                    getString(R.string.sb_act_app_pick_title),
                    labels,
                    sel,
                    idx -> {
                        String actionVal = StatusBarActions.ACTION_APP_PREFIX + pkgs[idx];
                        prefs().edit().putString(prefKey, actionVal).apply();
                        host().mirrorStringToGlobal(prefKey, actionVal);
                        row.setSummary(StatusBarActions.formatLabel(ctx, actionVal));
                        toast(getString(R.string.toast_saved));
                    },
                    getString(R.string.sb_action_test),
                    idx -> {
                        StatusBarActions.performHaptic(
                                host(),
                                prefs().getBoolean(KEY_SB_TAP_VIBRATE, true),
                                prefs().getInt(KEY_SB_TAP_VIBRATE_STRENGTH, 1));
                        StatusBarActions.execute(host(), StatusBarActions.ACTION_APP_PREFIX + pkgs[idx]);
                    });
        }

        // ---------------------------------------------------------- status bar pickers

        private void pickEdgeSpace() {
            final CharSequence[] options = {
                    getString(R.string.sb_edge_samsung),
                    getString(R.string.sb_edge_tight),
                    getString(R.string.sb_edge_custom)
            };
            int curMode = Math.max(0, Math.min(2, prefs().getInt(KEY_SB_EDGE_MODE, 0)));
            OneUiDialog.showSingleChoice(host(), getString(R.string.sb_row_edge_space), options, curMode, idx -> {
                if (idx == 2) {
                    int curDp = prefs().getInt(KEY_SB_EDGE_DP, 4);
                    OneUiDialog.showInput(host(), getString(R.string.sb_edge_custom_title),
                            getString(R.string.sb_edge_custom_hint), String.valueOf(curDp),
                            getString(R.string.action_set), value -> {
                                try {
                                    int dp = Math.max(0, Math.min(32, Integer.parseInt(value)));
                                    prefs().edit().putInt(KEY_SB_EDGE_MODE, 2).putInt(KEY_SB_EDGE_DP, dp).apply();
                                    host().mirrorIntToGlobal(KEY_SB_EDGE_MODE, 2);
                                    host().mirrorIntToGlobal(KEY_SB_EDGE_DP, dp);
                                    syncStatusBarSummaries();
                                    toast(getString(R.string.toast_saved));
                                } catch (NumberFormatException e) {
                                    toast(getString(R.string.toast_invalid_value));
                                }
                            });
                } else {
                    prefs().edit().putInt(KEY_SB_EDGE_MODE, idx).apply();
                    host().mirrorIntToGlobal(KEY_SB_EDGE_MODE, idx);
                    syncStatusBarSummaries();
                    toast(getString(R.string.toast_saved));
                }
            });
        }

        private void pickItemSpacing() {
            int cur = prefs().getInt(KEY_SB_ITEM_SPACING, -1);
            int start = cur < 0 ? 4 : Math.max(0, Math.min(12, cur));
            OneUiDialog.showSlider(
                    host(),
                    getString(R.string.sb_row_item_spacing),
                    getString(R.string.sb_item_spacing_sub),
                    0, 12, start,
                    v -> getString(R.string.sb_item_spacing_val, v),
                    getString(R.string.sb_reset), -1,
                    getString(R.string.sb_save),
                    value -> {
                        prefs().edit().putInt(KEY_SB_ITEM_SPACING, value).apply();
                        host().mirrorIntToGlobal(KEY_SB_ITEM_SPACING, value);
                        syncStatusBarSummaries();
                        toast(getString(R.string.toast_saved));
                    });
        }

        private void pickMaxNotifIcons() {
            int cur = prefs().getInt(KEY_SB_MAX_NOTIF_ICONS, 0);
            int start = cur <= 0 ? 3 : Math.max(1, Math.min(10, cur));
            OneUiDialog.showSlider(
                    host(),
                    getString(R.string.sb_row_max_notif_icons),
                    getString(R.string.sb_max_notif_sub),
                    1, 10, start,
                    v -> getString(R.string.sb_max_notif_val, v),
                    getString(R.string.sb_reset), 0,
                    getString(R.string.sb_save),
                    value -> {
                        prefs().edit().putInt(KEY_SB_MAX_NOTIF_ICONS, value).apply();
                        host().mirrorIntToGlobal(KEY_SB_MAX_NOTIF_ICONS, value);
                        syncStatusBarSummaries();
                        toast(getString(R.string.toast_saved));
                    });
        }

        private void pickHiddenIcons() {
            String raw = Settings.Secure.getString(host().getContentResolver(), "icon_blacklist");
            if (raw == null || raw.isEmpty()) {
                raw = prefs().getString(KEY_SB_HIDDEN_ICONS, "");
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
                    host(),
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
                        prefs().edit().putString(KEY_SB_HIDDEN_ICONS, joined).apply();
                        host().mirrorStringToGlobal(KEY_SB_HIDDEN_ICONS, joined);
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
            int cur = Math.max(0, Math.min(2, prefs().getInt(KEY_SB_TAP_VIBRATE_STRENGTH, 1)));
            OneUiDialog.showSingleChoice(host(), getString(R.string.sb_row_vibrate_strength), options, cur, idx -> {
                prefs().edit().putInt(KEY_SB_TAP_VIBRATE_STRENGTH, idx).apply();
                host().mirrorIntToGlobal(KEY_SB_TAP_VIBRATE_STRENGTH, idx);
                syncVibrateStrengthSummary();
                StatusBarActions.performHaptic(host(), prefs().getBoolean(KEY_SB_TAP_VIBRATE, true), idx);
                toast(getString(R.string.toast_saved));
            });
        }

        private void pickNetSpeedMode() {
            final CharSequence[] options = {
                    getString(R.string.sb_net_mode_total),
                    getString(R.string.sb_net_mode_both),
                    getString(R.string.sb_net_mode_down)
            };
            int cur = Math.max(0, Math.min(2, prefs().getInt(KEY_SB_NET_SPEED_MODE, 0)));
            OneUiDialog.showSingleChoice(host(), getString(R.string.sb_row_net_speed_mode), options, cur, idx -> {
                prefs().edit().putInt(KEY_SB_NET_SPEED_MODE, idx).apply();
                host().mirrorIntToGlobal(KEY_SB_NET_SPEED_MODE, idx);
                syncStatusBarSummaries();
                toast(getString(R.string.toast_saved));
            });
        }

        private void syncStatusBarSummaries() {
            if (getActivity() == null) return;
            if (mSbEdgeSpaceRow != null) {
                int mode = prefs().getInt(KEY_SB_EDGE_MODE, 0);
                if (mode == 1) {
                    mSbEdgeSpaceRow.setSummary(R.string.sb_edge_tight);
                } else if (mode == 2) {
                    mSbEdgeSpaceRow.setSummary(getString(R.string.sb_edge_custom_val, prefs().getInt(KEY_SB_EDGE_DP, 4)));
                } else {
                    mSbEdgeSpaceRow.setSummary(R.string.sb_edge_samsung);
                }
            }
            if (mSbItemSpacingRow != null) {
                int sp = prefs().getInt(KEY_SB_ITEM_SPACING, -1);
                mSbItemSpacingRow.setSummary(sp < 0
                        ? getString(R.string.sb_edge_samsung)
                        : getString(R.string.sb_item_spacing_val, sp));
            }
            if (mSbMaxNotifRow != null) {
                int max = prefs().getInt(KEY_SB_MAX_NOTIF_ICONS, 0);
                mSbMaxNotifRow.setSummary(max <= 0
                        ? getString(R.string.sb_edge_samsung)
                        : getString(R.string.sb_max_notif_val, max));
            }
            if (mSbHiddenIconsRow != null) {
                String raw = Settings.Secure.getString(host().getContentResolver(), "icon_blacklist");
                if (raw == null) raw = prefs().getString(KEY_SB_HIDDEN_ICONS, "");
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
                mSbHiddenIconsRow.setSummary(count == 0
                        ? getString(R.string.sb_hidden_none)
                        : getString(R.string.sb_hidden_count, count));
            }
            if (mSbNetSpeedModeRow != null) {
                int mode = prefs().getInt(KEY_SB_NET_SPEED_MODE, 0);
                int res = mode == 1 ? R.string.sb_net_mode_both
                        : (mode == 2 ? R.string.sb_net_mode_down : R.string.sb_net_mode_total);
                mSbNetSpeedModeRow.setSummary(res);
            }
        }

        private void syncVibrateStrengthSummary() {
            if (mSbVibrateStrengthRow == null || getActivity() == null) return;
            int cur = prefs().getInt(KEY_SB_TAP_VIBRATE_STRENGTH, 1);
            int res = cur == 0 ? R.string.sb_vib_light : (cur == 2 ? R.string.sb_vib_strong : R.string.sb_vib_medium);
            mSbVibrateStrengthRow.setSummary(res);
        }

        private void syncDependencies() {
            boolean modeOn = prefs().getBoolean(KEY_DISPLAY_SCREEN_MODE, true);
            boolean timeoutOn = prefs().getBoolean(KEY_DISPLAY_SCREEN_TIMEOUT, true);
            boolean actionOn = prefs().getBoolean(KEY_DISPLAY_ACTION_TIMEOUT, true);
            boolean vibOn = prefs().getBoolean(KEY_SB_TAP_VIBRATE, true);
            boolean netSpeedOn = prefs().getBoolean(KEY_SB_NET_SPEED, false);
            if (mScreenModeRow != null) mScreenModeRow.setEnabled(modeOn);
            if (mScreenTimeoutRow != null) mScreenTimeoutRow.setEnabled(timeoutOn);
            if (mActionTimeoutRow != null) mActionTimeoutRow.setEnabled(actionOn);
            if (mSbVibrateStrengthRow != null) mSbVibrateStrengthRow.setEnabled(vibOn);
            if (mSbNetSpeedModeRow != null) mSbNetSpeedModeRow.setEnabled(netSpeedOn);
            if (mSbNetSpeedAutoHideSwitch != null) mSbNetSpeedAutoHideSwitch.setEnabled(netSpeedOn);
        }

        // ---------------------------------------------------------- display pickers

        private void pickScreenMode() {
            final CharSequence[] names = {
                    getString(R.string.mode_vivid),
                    getString(R.string.mode_natural),
                    getString(R.string.mode_cinema),
                    getString(R.string.mode_photo),
                    getString(R.string.mode_basic)
            };
            int currentAuto = Settings.System.getInt(host().getContentResolver(),
                    "screen_mode_automatic_setting", 1);
            int currentMode = Settings.System.getInt(host().getContentResolver(), "screen_mode_setting", 4);
            int effective = currentAuto == 1 ? 4 : currentMode;
            int selected = 0;
            for (int i = 0; i < SCREEN_MODE_VALUES.length; i++) {
                if (SCREEN_MODE_VALUES[i] == effective) {
                    selected = i;
                    break;
                }
            }
            mScreenModeRow.setSummary(names[selected]);
            OneUiDialog.showSingleChoice(host(), getString(R.string.title_display_screen_mode),
                    names, selected, index -> {
                        int value = SCREEN_MODE_VALUES[index];
                        putSystemSetting("screen_mode_automatic_setting", value == 4 ? 1 : 0);
                        putSystemSetting("screen_mode_setting", value);
                        applyMdnieMode(value);
                        mScreenModeRow.setSummary(names[index]);
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
            mScreenTimeoutRow.setSummary(names[startIndex]);
            OneUiDialog.showSingleChoice(host(), getString(R.string.title_display_screen_timeout),
                    names, startIndex, index -> {
                        long value = SCREEN_TIMEOUT_VALUES[index];
                        putSystemSettingLong("screen_off_timeout", value);
                        mScreenTimeoutRow.setSummary(names[index]);
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
            mActionTimeoutRow.setSummary(describeActionTimeout(current));
            OneUiDialog.showSingleChoice(host(), getString(R.string.title_display_action_timeout),
                    names, selected, index -> {
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
            OneUiDialog.showInput(host(), getString(R.string.action_custom_title),
                    getString(R.string.action_custom_hint), initial, getString(R.string.action_set),
                    value -> {
                        if (value.isEmpty()) return;
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
                mActionTimeoutRow.setSummary(describeActionTimeout(ms));
            }
            toast(getString(R.string.toast_action_timeout, describeActionTimeout(ms)));
        }

        private void syncScreenModeFromSystem() {
            if (mScreenModeRow == null || getActivity() == null) return;
            int currentAuto = Settings.System.getInt(host().getContentResolver(),
                    "screen_mode_automatic_setting", 1);
            int currentMode = Settings.System.getInt(host().getContentResolver(), "screen_mode_setting", 4);
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
                    mScreenModeRow.setSummary(names[i]);
                    break;
                }
            }
        }

        private void syncScreenTimeoutFromSystem() {
            if (mScreenTimeoutRow == null || getActivity() == null) return;
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
            mScreenTimeoutRow.setSummary(names[indexOfTimeout(currentTimeout())]);
        }

        private void syncActionTimeoutFromSystem() {
            if (mActionTimeoutRow == null || getActivity() == null) return;
            mActionTimeoutRow.setSummary(describeActionTimeout(currentActionTimeoutMs()));
        }

        // ---------------------------------------------------------- live battery stats

        private void registerBatteryReceiver() {
            if (getActivity() == null) return;
            try {
                Intent sticky = getActivity().registerReceiver(mBatteryReceiver,
                        new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                mBatteryReceiverRegistered = true;
                if (sticky != null) {
                    updateBattery(sticky);
                }
            } catch (Throwable ignored) {
            }
        }

        private void updateBattery(Intent intent) {
            if (mBattLevelRow == null || getActivity() == null) return;
            BatteryManager bm = (BatteryManager) getActivity().getSystemService(Context.BATTERY_SERVICE);

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
                status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN);
                health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN);
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
                if (counter > 0) chargeCounterUah = counter;
            }
            if (scale <= 0) scale = 100;

            int percent = level < 0 ? -1 : Math.round(level * 100f / scale);
            setRowSummary(mBattLevelRow, percent < 0 ? "—" : percent + " %");
            setRowSummary(mBattStatusRow, statusLabel(status));
            setRowSummary(mBattSourceRow, sourceLabel(plugged));
            setRowSummary(mBattHealthRow, healthLabel(health));
            setRowSummary(mBattTempRow, temperature > 0
                    ? String.format(Locale.getDefault(), "%.1f \u00B0C", temperature / 10f) : "—");
            setRowSummary(mBattVoltageRow, voltageMv > 0
                    ? String.format(Locale.getDefault(), "%.2f V", voltageMv / 1000f) : "—");

            long currentMa = 0L;
            if (intent != null) {
                int extraNow = intent.getIntExtra("current_now", 0);
                int extraAvg = intent.getIntExtra("current_avg", 0);
                if (extraNow != 0) currentMa = extraNow;
                else if (extraAvg != 0) currentMa = extraAvg;
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
            setRowSummary(mBattCurrentRow, currentMa == 0L ? "—" : absCurrentMa + " mA");

            if (voltageMv > 0 && absCurrentMa > 0) {
                double watts = (voltageMv / 1000.0) * (absCurrentMa / 1000.0);
                setRowSummary(mBattPowerRow, String.format(Locale.getDefault(), "%.1f W", watts));
            } else {
                setRowSummary(mBattPowerRow, "—");
            }

            setRowSummary(mBattChargeRow, chargeCounterUah <= 0 ? "—" : (chargeCounterUah / 1000L) + " mAh");
            setRowSummary(mBattTechRow, (technology != null && !technology.isEmpty()) ? technology : "—");

            long remaining = -1L;
            if (bm != null) {
                try {
                    remaining = bm.computeChargeTimeRemaining();
                } catch (Throwable ignored) {
                }
            }
            if (status == BatteryManager.BATTERY_STATUS_CHARGING && remaining > 0) {
                long minutes = remaining / 60000L;
                setRowSummary(mBattRemainingRow, minutes >= 60
                        ? getString(R.string.batt_hours_minutes, minutes / 60, minutes % 60)
                        : getString(R.string.batt_minutes, minutes));
            } else {
                setRowSummary(mBattRemainingRow, "—");
            }

            setRowSummary(mBattProtectRow, protectionLabel());
        }

        private static void setRowSummary(Preference row, CharSequence value) {
            if (row != null) row.setSummary(value);
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

        private String protectionLabel() {
            int mode = Settings.Global.getInt(host().getContentResolver(), "protect_battery", 0);
            if (mode == 0) return getString(R.string.batt_protect_off);
            if (mode == 4) {
                int maxPct = Settings.Global.getInt(host().getContentResolver(), "protect_battery_max_level", 100);
                return getString(R.string.batt_protect_adaptive, maxPct);
            }
            int limit = Settings.Global.getInt(host().getContentResolver(), "protect_battery_limit", 80);
            return getString(R.string.batt_protect_on, limit);
        }

        // ---------------------------------------------------------- system helpers

        private void restartSystemUi() {
            toast(getString(R.string.toast_restarting_systemui));
            Context ctx = host();
            try {
                Settings.Global.putLong(ctx.getContentResolver(),
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
            }, "s23enh-restart-sysui").start();
        }

        private long currentTimeout() {
            long value = Settings.System.getLong(host().getContentResolver(), "screen_off_timeout", 30000L);
            if (value >= Integer.MAX_VALUE - 8L || value < 0) return Integer.MAX_VALUE;
            return value;
        }

        private int indexOfTimeout(long timeout) {
            for (int i = 0; i < SCREEN_TIMEOUT_VALUES.length; i++) {
                if (SCREEN_TIMEOUT_VALUES[i] == timeout) return i;
            }
            return timeout >= Integer.MAX_VALUE - 8L ? SCREEN_TIMEOUT_VALUES.length - 1 : 1;
        }

        private int currentActionTimeoutMs() {
            return Settings.Secure.getInt(host().getContentResolver(),
                    "accessibility_interactive_ui_timeout_ms", 0);
        }

        private String describeActionTimeout(int ms) {
            if (ms <= 0) return getString(R.string.action_default);
            if (ms % 60000 == 0) return (ms / 60000) + " min";
            return (ms / 1000) + " s";
        }

        private void putSystemSetting(String key, int value) {
            try {
                Settings.System.putInt(host().getContentResolver(), key, value);
            } catch (Throwable t) {
                runSettingsPutAsync("system", key, String.valueOf(value));
            }
        }

        private void putSystemSettingLong(String key, long value) {
            try {
                Settings.System.putLong(host().getContentResolver(), key, value);
            } catch (Throwable t) {
                runSettingsPutAsync("system", key, String.valueOf(value));
            }
        }

        private void putSecureSetting(String key, String value) {
            try {
                Settings.Secure.putString(host().getContentResolver(), key, value);
            } catch (Throwable t) {
                runSettingsPutAsync("secure", key, value);
            }
        }

        private void applyMdnieMode(int mode) {
            try {
                Object mdnie = host().getSystemService("mDNIe");
                if (mdnie != null) {
                    Method m = mdnie.getClass().getMethod("setScreenMode", int.class);
                    m.invoke(mdnie, mode);
                }
            } catch (Throwable ignored) {
            }
        }

        private static void runSettingsPutAsync(String namespace, String key, String value) {
            new Thread(() -> {
                try {
                    Process p = Runtime.getRuntime().exec(new String[]{"settings", "put", namespace, key, value});
                    p.waitFor();
                } catch (Throwable ignored) {
                }
            }, "s23enh-settings-put").start();
        }
    }
}
