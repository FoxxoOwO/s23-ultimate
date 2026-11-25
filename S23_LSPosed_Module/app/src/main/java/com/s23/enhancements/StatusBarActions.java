package com.s23.enhancements;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.input.InputManager;
import android.media.AudioManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.provider.Settings;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.View;

import java.lang.reflect.Method;

/**
 * Shared status bar tap/long-press actions and haptic feedback helpers.
 * Used both by SystemUiHooks (inside com.android.systemui) and by MainActivity
 * (for picker labels and the "Test" button).
 */
public final class StatusBarActions {

    private static final String TAG = "S23Enhancer";

    public static final String ACTION_NONE = "none";
    public static final String ACTION_SLEEP = "sleep";
    public static final String ACTION_POWER_SAVING = "power_saving";
    public static final String ACTION_BATTERY_USAGE = "battery_usage";
    public static final String ACTION_ALARMS = "alarms";
    public static final String ACTION_TIMER = "timer";
    public static final String ACTION_FINDER = "finder";
    public static final String ACTION_FLASHLIGHT = "flashlight";
    public static final String ACTION_SCREENSHOT = "screenshot";
    public static final String ACTION_EXPAND_NOTIFICATIONS = "expand_notifications";
    public static final String ACTION_EXPAND_QS = "expand_qs";
    public static final String ACTION_WIFI_TOGGLE = "wifi_toggle";
    public static final String ACTION_WIFI_SETTINGS = "wifi_settings";
    public static final String ACTION_MOBILE_SETTINGS = "mobile_settings";
    public static final String ACTION_BLUETOOTH_SETTINGS = "bluetooth_settings";
    public static final String ACTION_VOLUME_PANEL = "volume_panel";
    public static final String ACTION_BACK = "back";
    public static final String ACTION_HOME = "home";
    public static final String ACTION_RECENTS = "recents";

    public static final String[] ACTION_IDS = {
            ACTION_NONE,
            ACTION_SLEEP,
            ACTION_POWER_SAVING,
            ACTION_BATTERY_USAGE,
            ACTION_ALARMS,
            ACTION_TIMER,
            ACTION_FINDER,
            ACTION_FLASHLIGHT,
            ACTION_SCREENSHOT,
            ACTION_EXPAND_NOTIFICATIONS,
            ACTION_EXPAND_QS,
            ACTION_WIFI_TOGGLE,
            ACTION_WIFI_SETTINGS,
            ACTION_MOBILE_SETTINGS,
            ACTION_BLUETOOTH_SETTINGS,
            ACTION_VOLUME_PANEL,
            ACTION_BACK,
            ACTION_HOME,
            ACTION_RECENTS
    };

    private static volatile boolean sTorchOn = false;

    /** SystemUI BatteryControllerImpl instance captured by SystemUiHooks as fallback. */
    public static volatile Object sBatteryController;
    private static final Uri DC_API = Uri.parse("content://com.samsung.android.sm.dcapi");

    private StatusBarActions() {
    }

    public static int labelResFor(String actionId) {
        if (actionId == null) return R.string.sb_act_none;
        switch (actionId) {
            case ACTION_SLEEP:
                return R.string.sb_act_sleep;
            case ACTION_POWER_SAVING:
                return R.string.sb_act_power_saving;
            case ACTION_BATTERY_USAGE:
                return R.string.sb_act_battery_usage;
            case ACTION_ALARMS:
                return R.string.sb_act_alarms;
            case ACTION_TIMER:
                return R.string.sb_act_timer;
            case ACTION_FINDER:
                return R.string.sb_act_finder;
            case ACTION_FLASHLIGHT:
                return R.string.sb_act_flashlight;
            case ACTION_SCREENSHOT:
                return R.string.sb_act_screenshot;
            case ACTION_EXPAND_NOTIFICATIONS:
                return R.string.sb_act_expand_notifications;
            case ACTION_EXPAND_QS:
                return R.string.sb_act_expand_qs;
            case ACTION_WIFI_TOGGLE:
                return R.string.sb_act_wifi_toggle;
            case ACTION_WIFI_SETTINGS:
                return R.string.sb_act_wifi_settings;
            case ACTION_MOBILE_SETTINGS:
                return R.string.sb_act_mobile_settings;
            case ACTION_BLUETOOTH_SETTINGS:
                return R.string.sb_act_bluetooth_settings;
            case ACTION_VOLUME_PANEL:
                return R.string.sb_act_volume_panel;
            case ACTION_BACK:
                return R.string.sb_act_back;
            case ACTION_HOME:
                return R.string.sb_act_home;
            case ACTION_RECENTS:
                return R.string.sb_act_recents;
            default:
                return R.string.sb_act_none;
        }
    }

    public static void performHaptic(Context ctx, boolean enabled, int strength) {
        performHaptic(null, ctx, enabled, strength, false);
    }

    public static void performHaptic(View view, Context ctx, boolean enabled, int strength, boolean isLongPress) {
        if (!enabled) return;
        if (view != null) {
            try {
                view.performHapticFeedback(isLongPress
                        ? HapticFeedbackConstants.LONG_PRESS
                        : HapticFeedbackConstants.CONTEXT_CLICK);
            } catch (Throwable ignored) {
            }
        }
        if (ctx == null) return;
        int ms = strength == 0 ? 15 : (strength == 2 ? 40 : 25);
        int amp = strength == 0 ? 80 : (strength == 2 ? 255 : 160);
        try {
            Vibrator vibrator = null;
            VibratorManager vm = ctx.getSystemService(VibratorManager.class);
            if (vm != null) {
                vibrator = vm.getDefaultVibrator();
            }
            if (vibrator == null) {
                vibrator = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
            }
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createOneShot(ms, amp));
            }
        } catch (Throwable ignored) {
        }
    }

    public static void execute(Context ctx, String actionId) {
        if (ctx == null || actionId == null || ACTION_NONE.equals(actionId)) return;
        try {
            switch (actionId) {
                case ACTION_SLEEP:
                    goToSleep(ctx);
                    break;
                case ACTION_POWER_SAVING:
                    togglePowerSaving(ctx);
                    break;
                case ACTION_BATTERY_USAGE:
                    launchActivity(ctx, new Intent(Intent.ACTION_POWER_USAGE_SUMMARY));
                    break;
                case ACTION_ALARMS:
                    launchAlarms(ctx);
                    break;
                case ACTION_TIMER:
                    launchTimer(ctx);
                    break;
                case ACTION_FINDER:
                    launchFinder(ctx);
                    break;
                case ACTION_FLASHLIGHT:
                    toggleFlashlight(ctx);
                    break;
                case ACTION_SCREENSHOT:
                    injectKey(KeyEvent.KEYCODE_SYSRQ);
                    break;
                case ACTION_EXPAND_NOTIFICATIONS:
                    invokeStatusBarMethod(ctx, "expandNotificationsPanel");
                    break;
                case ACTION_EXPAND_QS:
                    invokeStatusBarMethod(ctx, "expandSettingsPanel");
                    break;
                case ACTION_WIFI_TOGGLE:
                    toggleWifi(ctx);
                    break;
                case ACTION_WIFI_SETTINGS:
                    launchActivity(ctx, new Intent(Settings.ACTION_WIFI_SETTINGS));
                    break;
                case ACTION_MOBILE_SETTINGS:
                    try {
                        launchActivity(ctx, new Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS));
                    } catch (Throwable ignored) {
                        launchActivity(ctx, new Intent(Settings.ACTION_WIRELESS_SETTINGS));
                    }
                    break;
                case ACTION_BLUETOOTH_SETTINGS:
                    launchActivity(ctx, new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
                    break;
                case ACTION_VOLUME_PANEL:
                    AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
                    if (am != null) {
                        am.adjustVolume(AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI);
                    }
                    break;
                case ACTION_BACK:
                    injectKey(KeyEvent.KEYCODE_BACK);
                    break;
                case ACTION_HOME:
                    launchActivity(ctx, new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME));
                    break;
                case ACTION_RECENTS:
                    injectKey(KeyEvent.KEYCODE_APP_SWITCH);
                    break;
                default:
                    break;
            }
        } catch (Throwable t) {
            Log.w(TAG, "Action failed (" + actionId + "): " + t.getMessage());
        }
    }

    private static void goToSleep(Context ctx) throws Throwable {
        PowerManager pm = ctx.getSystemService(PowerManager.class);
        if (pm != null) {
            Method m = PowerManager.class.getMethod("goToSleep", long.class);
            m.invoke(pm, SystemClock.uptimeMillis());
        }
    }

    private static void togglePowerSaving(Context ctx) {
        boolean turnOn = Settings.Global.getInt(ctx.getContentResolver(), "low_power", 0) == 0;
        new Thread(() -> {
            String method = turnOn ? "psm_turn_on" : "psm_turn_off";
            try {
                Bundle r = ctx.getContentResolver().call(DC_API, method, null, null);
                if (r != null && r.getBoolean("result", false)) {
                    return;
                }
            } catch (Throwable t) {
                Log.w(TAG, "dcapi " + method + " failed: " + t.getMessage());
            }

            Object bc = sBatteryController;
            if (bc != null) {
                try {
                    Class<?> exp = bc.getClass().getClassLoader().loadClass("com.android.systemui.animation.Expandable");
                    bc.getClass().getMethod("setPowerSaveMode", boolean.class, exp).invoke(bc, turnOn, null);
                    return;
                } catch (Throwable t) {
                    Log.w(TAG, "BatteryController fallback failed: " + t.getMessage());
                }
            }

            try {
                Settings.Global.putInt(ctx.getContentResolver(), "low_power", turnOn ? 1 : 0);
            } catch (Throwable ignored) {
            }
        }, "s23enh-psm").start();
    }

    private static void launchAlarms(Context ctx) {
        try {
            launchActivity(ctx, new Intent("android.intent.action.SHOW_ALARMS"));
        } catch (Throwable t) {
            Intent fallback = ctx.getPackageManager().getLaunchIntentForPackage("com.sec.android.app.clockpackage");
            if (fallback != null) {
                launchActivity(ctx, fallback);
            }
        }
    }

    private static void launchTimer(Context ctx) {
        try {
            launchActivity(ctx, new Intent("android.intent.action.SHOW_TIMERS"));
        } catch (Throwable t) {
            try {
                launchActivity(ctx, new Intent("android.intent.action.SET_TIMER"));
            } catch (Throwable t2) {
                Intent fallback = ctx.getPackageManager().getLaunchIntentForPackage("com.sec.android.app.clockpackage");
                if (fallback != null) {
                    launchActivity(ctx, fallback);
                }
            }
        }
    }

    private static void launchFinder(Context ctx) {
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(
                    "com.samsung.android.app.galaxyfinder",
                    "com.samsung.android.app.galaxyfinder.GalaxyFinderActivity"));
            launchActivity(ctx, intent);
        } catch (Throwable t) {
            try {
                Intent pkgIntent = ctx.getPackageManager()
                        .getLaunchIntentForPackage("com.samsung.android.app.galaxyfinder");
                if (pkgIntent != null) {
                    launchActivity(ctx, pkgIntent);
                    return;
                }
            } catch (Throwable ignored) {
            }
            launchActivity(ctx, new Intent(Intent.ACTION_SEARCH));
        }
    }

    private static void toggleFlashlight(Context ctx) throws Throwable {
        CameraManager cm = (CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);
        if (cm == null) return;
        for (String id : cm.getCameraIdList()) {
            CameraCharacteristics cc = cm.getCameraCharacteristics(id);
            Boolean hasFlash = cc.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
            Integer facing = cc.get(CameraCharacteristics.LENS_FACING);
            if (Boolean.TRUE.equals(hasFlash)
                    && facing != null
                    && facing == CameraCharacteristics.LENS_FACING_BACK) {
                boolean next = !sTorchOn;
                cm.setTorchMode(id, next);
                sTorchOn = next;
                return;
            }
        }
    }

    private static void toggleWifi(Context ctx) {
        WifiManager wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wm != null) {
            @SuppressWarnings("deprecation")
            boolean enabled = wm.isWifiEnabled();
            @SuppressWarnings("deprecation")
            boolean ok = wm.setWifiEnabled(!enabled);
            if (!ok) {
                launchActivity(ctx, new Intent(Settings.ACTION_WIFI_SETTINGS));
            }
        }
    }

    private static void invokeStatusBarMethod(Context ctx, String methodName) {
        try {
            Object sb = ctx.getSystemService("statusbar");
            if (sb != null) {
                Method m = sb.getClass().getMethod(methodName);
                m.invoke(sb);
            }
        } catch (Throwable t) {
            Log.w(TAG, "StatusBar method " + methodName + " failed: " + t.getMessage());
        }
    }

    private static void launchActivity(Context ctx, Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        ctx.startActivity(intent);
    }

    public static void injectKey(int keyCode) {
        try {
            Method getInstance = InputManager.class.getMethod("getInstance");
            InputManager im = (InputManager) getInstance.invoke(null);
            if (im == null) return;
            Method inject = InputManager.class.getMethod("injectInputEvent", InputEvent.class, int.class);
            long now = SystemClock.uptimeMillis();
            KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0);
            KeyEvent up = new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0);
            inject.invoke(im, down, 0);
            inject.invoke(im, up, 0);
        } catch (Throwable t) {
            Log.w(TAG, "injectKey(" + keyCode + ") failed: " + t.getMessage());
        }
    }
}
