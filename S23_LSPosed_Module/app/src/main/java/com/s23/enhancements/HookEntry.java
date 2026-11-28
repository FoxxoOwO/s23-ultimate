package com.s23.enhancements;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.camera2.CaptureRequest;
import android.os.Process;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam;
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;

public class HookEntry extends XposedModule {

    private static final String TAG = "S23Enhancer";

    /** Camera: allow muting the shutter sound regardless of the regional CSC restriction. */
    private static final String PREF_CAM_SHUTTER_MUTE = "pref_camera_shutter_mute";

    /** Camera: expose UHD 120 fps in Slow motion (stock feature map keeps it pro_video only). */
    private static final String PREF_CAM_4K120_SLOWMO = "pref_camera_4k120_slowmo";

    /** Camera: skip the picture post-processing node chains (HDR fusion, AI ISP, SR, beauty...). */
    private static final String PREF_CAM_SKIP_POSTPROC = "pref_camera_skip_postproc";

    /** Key of the switch injected into the Camera settings screen (Camera's own prefs). */
    private static final String CAM_UI_PREF_KEY = "s23enh_skip_postproc";

    /** core2 NodeChain keys that only alter pixels; a disabled chain passes its input through. */
    private static final java.util.Set<String> POST_PROC_CHAINS = new java.util.HashSet<>(Arrays.asList(
            "AI_ISP", "AI_ISP_ZOOM", "AI_HIGH_RES", "AI_CLEAR_ZOOM", "HIGH_RES", "FUSION_HIGH_RES",
            "SR", "UW_SR", "RAW_SR", "MACRO_RAW_SR", "TETRA_SR", "SUPER_NIGHT",
            "LL_HDR", "MF_HDR", "AEB_HDR", "HYBRID_HDR", "SS_HDR", "HIFI_LLS",
            "LOCAL_TM", "DE_FLICKER", "DE_FLICKER_HDR", "SIE", "FACE_RESTORATION",
            "BEAUTY", "SELFIE_CORRECTION"));
    private final java.util.Set<String> mLoggedSkippedChains =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Battery info: unlock the EU-regulatory rows (health, cycles, dates) in Settings. */
    private static final String PREF_BATTERY_INFO = "pref_system_battery_info";

    /** Settings App Info: inject Package name (tap to copy) and Install/Update date rows. */
    private static final String PREF_APP_INFO_DETAILS = "pref_system_app_info_details";

    /** Settings Fingerprints: keep "Improve accuracy" (R.id.update) in fingerprint option popup. */
    private static final String PREF_FP_IMPROVE_ACCURACY = "pref_system_fp_improve_accuracy";
    private static final ThreadLocal<Boolean> sInFpOptionMenu = new ThreadLocal<>();
    private volatile int mFpUpdateMenuId = -1;

    /** Lock screen: max ComplicationWidget grid rows (0 = Samsung default 1 row, 2-3 = expanded). */
    private static final String PREF_LOCKSCREEN_WIDGET_ROWS = "pref_lockscreen_widget_rows";
    private static final String COMPLICATION_SIZE_MANAGER_CLASS =
            "com.samsung.android.app.facewidget.complication.ComplicationWidgetSizeManager";
    private final java.util.Set<Class<?>> mHookedComplicationManagers =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    private volatile boolean mComplicationHooked;
    private volatile boolean mLoggedLockscreenWidgetOverride;

    private static final CaptureRequest.Key<Boolean> KEY_DUAL_CAMERA_DISABLE =
            new CaptureRequest.Key<>("samsung.android.control.dualCameraDisable", Boolean.class);
    private static final CaptureRequest.Key<Long> KEY_SPECIAL_IMAGE_QUALITY_POLICY =
            new CaptureRequest.Key<>("samsung.android.control.specialImageQualityPolicy", Long.class);

    /** Cached camera toggles (read once per hooked camera process, hot path). */
    private volatile boolean mCamLogVideo = true;
    private volatile boolean mCamLenses = true;
    private volatile boolean mCamJpeg100 = true;
    private volatile boolean mCamShutterMute = true;
    private volatile boolean mCam4k120SlowMo = true;
    private volatile int mForcedTagCount;
    private int mCamResolutionPatchCount;

    /**
     * Cross-process pref channel. LSPosed remote prefs are not available in every hooked
     * process (they came back empty on One UI 9), so the companion app mirrors every toggle
     * into Settings.Global and the hooks read it from there.
     */
    private static final String GLOBAL_PREFIX = "s23enh_";

    private SharedPreferences mPrefs;
    private volatile Context mAppContext;
    private Context mSystemContext;
    private String mProcessName;

    void setAppContext(Context ctx) {
        if (ctx == null) return;
        Context app = ctx.getApplicationContext();
        mAppContext = app != null ? app : ctx;
    }

    public HookEntry() {
        super();
        Log.i(TAG, "=== HookEntry instantiated in PID " + Process.myPid() + " ===");
    }

    boolean isPref(String key, boolean defVal) {
        // 1) Settings.Global mirror written by the companion app (authoritative, never cached).
        try {
            Context ctx = systemContext();
            if (ctx != null) {
                int v = Settings.Global.getInt(ctx.getContentResolver(), GLOBAL_PREFIX + key, -1);
                if (v >= 0) return v != 0;
            }
        } catch (Throwable ignored) {
        }

        // 2) Legacy LSPosed remote preferences (may be empty depending on the build).
        if (mPrefs == null) {
            try {
                mPrefs = getRemotePreferences("s23_preferences");
            } catch (Throwable t) {
                // Ignore fallback to defVal
            }
        }
        if (mPrefs != null) {
            try {
                return mPrefs.getBoolean(key, defVal);
            } catch (Throwable ignored) {
            }
        }
        return defVal;
    }

    int getIntPref(String key, int defVal) {
        try {
            Context ctx = systemContext();
            if (ctx != null) {
                return Settings.Global.getInt(ctx.getContentResolver(), GLOBAL_PREFIX + key, defVal);
            }
        } catch (Throwable ignored) {
        }
        return defVal;
    }

    String getStringPref(String key, String defVal) {
        try {
            Context ctx = systemContext();
            if (ctx != null) {
                String v = Settings.Global.getString(ctx.getContentResolver(), GLOBAL_PREFIX + key);
                if (v != null) return v;
            }
        } catch (Throwable ignored) {
        }
        return defVal;
    }

    void hookConstructors(Class<?> clazz, XposedInterface.Hooker hooker) {
        if (clazz == null) return;
        for (Constructor<?> c : clazz.getDeclaredConstructors()) {
            try {
                hook(c).intercept(hooker);
            } catch (Throwable t) {
                Log.w(TAG, "Failed to hook constructor on " + clazz.getName() + ": " + t.getMessage());
            }
        }
    }

    /**
     * System context usable from any injected process, including before the app context
     * exists (onPackageReady runs before Application#onCreate).
     */
    private Context systemContext() {
        if (mAppContext != null) return mAppContext;
        if (mSystemContext != null) return mSystemContext;
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            try {
                Object app = at.getMethod("currentApplication").invoke(null);
                if (app instanceof Context) {
                    mAppContext = (Context) app;
                    return mAppContext;
                }
            } catch (Throwable ignored) {
            }
            Object thread = at.getMethod("currentActivityThread").invoke(null);
            if (thread == null) return null;
            Method getSystemContext = at.getDeclaredMethod("getSystemContext");
            getSystemContext.setAccessible(true);
            mSystemContext = (Context) getSystemContext.invoke(thread);
        } catch (Throwable ignored) {
        }
        return mSystemContext;
    }

    void hookAllMethods(Class<?> clazz, String methodName, XposedInterface.Hooker hooker) {
        if (clazz == null) return;
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getName().equals(methodName)) {
                try {
                    hook(m).intercept(hooker);
                } catch (Throwable t) {
                    Log.w(TAG, "Failed to hook method " + methodName + " on " + clazz.getName() + ": " + t.getMessage());
                }
            }
        }
    }

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        mProcessName = param.getProcessName();
        Log.i(TAG, "=== onModuleLoaded: process=" + mProcessName + ", isSystemServer=" + param.isSystemServer() + " ===");
    }

    @Override
    public void onSystemServerStarting(SystemServerStartingParam param) {
        Log.i(TAG, "=== onSystemServerStarting ===");
        try {
            ClassLoader cl = param.getClassLoader();
            hookSystemServer(cl);
            hookFloatingFeatures(cl);
            hookCscFeatures(cl);
            hookSystemProperties(cl);
        } catch (Throwable t) {
            Log.e(TAG, "Error in onSystemServerStarting", t);
        }
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        String pkg = param.getPackageName();
        Log.i(TAG, "=== onPackageReady: pkg=" + pkg + ", isFirst=" + param.isFirstPackage() + " ===");
        try {
            ClassLoader cl = param.getClassLoader();

            hookFloatingFeatures(cl);
            hookCscFeatures(cl);
            hookSystemProperties(cl);

            if ("com.sec.android.app.camera".equals(pkg)) {
                hookCamera(cl);
                hookCameraJpeg(cl);
                hookCameraShutterSound(cl);
                hookCameraPostProcessing(cl);
                hookCameraSettingsToggle(cl);
            } else if ("com.samsung.android.incallui".equals(pkg) ||
                       "com.samsung.android.dialer".equals(pkg) ||
                       "com.samsung.android.app.telephonyui".equals(pkg) ||
                       "com.android.phone".equals(pkg)) {
                hookCallRecording(cl);
            } else if ("com.android.settings".equals(pkg)) {
                hookSettings(cl);
            } else if ("com.android.systemui".equals(pkg)) {
                if (mProcessName == null || "com.android.systemui".equals(mProcessName)) {
                    hookSystemUI(cl);
                } else {
                    Log.i(TAG, "Skipping SystemUI hooks in secondary process: " + mProcessName);
                }
            } else if ("com.samsung.android.app.aodservice".equals(pkg)) {
                hookLockscreenWidgets(cl, pkg);
                ChargingInfoHooks.installInAod(this, cl);
            } else if ("com.samsung.android.app.dressroom".equals(pkg)) {
                hookLockscreenWidgets(cl, pkg);
            } else if ("com.sec.android.app.launcher".equals(pkg)) {
                RecentsMenuHooks.install(this, cl, pkg);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error in onPackageReady for " + pkg, t);
        }
    }

    /**
     * Samsung Camera hooks:
     * Unlocks Log Video and 4K60 lens switching (all optical lenses, 60/120 fps on tele).
     */

    // ---------------------------------------------------------------- camera device features

    /** Boolean device-feature tags (stable enum constant names, unlike the obfuscated classes). */
    private static final String[] CAM_LOG_VIDEO_TAGS = {
        "SUPPORT_LOG_VIDEO", "SUPPORT_FRONT_LOG_VIDEO"
    };

    private static final String[] CAM_LENS_TAGS = {
        // Optical lens table + seamless (lens) switching while recording UHD 60 fps.
        // NOTE: only tags that are already true on a stock S23 Ultra are listed; the ones that
        // crash the bokeh pipeline on this firmware (SUPPORT_BACK_PORTRAIT_SECOND_TELE_DUAL,
        // SUPPORT_BACK_PORTRAIT_VIDEO_SEAMLESS_ZOOM, SUPPORT_PORTRAIT_SEAMLESS_ZOOM) and the
        // unsupported tele 120 fps tags are deliberately NOT forced.
        "SUPPORT_BACK_WIDE_CAMERA",
        "SUPPORT_BACK_TELE_CAMERA",
        "SUPPORT_BACK_SECOND_TELE_CAMERA",
        "SUPPORT_BACK_WIDE_PRO",
        "SUPPORT_BACK_TELE_PRO",
        "SUPPORT_BACK_SECOND_TELE_PRO",
        "SUPPORT_SEAMLESS_ZOOM",
        "SUPPORT_CAMERA_ASSISTANT_AUTO_LENS_SWITCHING",
        "SUPPORT_PRO_VIDEO_WIDE_LENS_60FPS",
        "SUPPORT_PRO_VIDEO_TELE_LENS_60FPS",
        "SUPPORT_PRO_VIDEO_SECOND_TELE_LENS_60FPS"
    };

    /**
     * Shutter sound tags. SUPPORT_SHUTTER_SOUND_MENU is what makes the "Shutter sound" row
     * appear in the camera settings; the actual mute is decided by isPlaySoundAvailable()
     * (a8.v.g) which reads setting SHUTTER_SOUND (see the hook below).
     */
    private static final String CAM_SHUTTER_MENU_TAG = "SUPPORT_SHUTTER_SOUND_MENU";

    /** Zoom levels of the 3x and 10x tele lenses. */
    private static final String CAM_TELE_ZOOM_TAG = "BACK_TELE_CAMERA_ZOOM_LEVEL";
    private static final String CAM_SECOND_TELE_ZOOM_TAG = "BACK_SECOND_TELE_CAMERA_ZOOM_LEVEL";

    // ------------------------------------------------- per-resolution camcorder feature map

    /**
     * Obfuscated holders of the parsed camera-feature.xml map. One UI 9 (camera 17.x) exposes the
     * per-resolution camcorder map through e5.g.n(Le5.l) / e5.f.r1(Le5.l); older builds used
     * CameraResolutionMap#getBackCamcorderFeature. The class names are version specific, the tag
     * enum constant names (BACK_CAMCORDER_RESOLUTION_FEATURE_MAP_...) are not, so the methods are
     * discovered by signature (one enum argument, Map result) instead of by name.
     */
    private static final String[] FEATURE_MAP_CLASS_CANDIDATES = {
        "e5.g", "e5.f", "com.sec.android.app.camera.util.CameraResolutionMap"
    };

    private static final String BACK_UHD_60 =
            "BACK_CAMCORDER_RESOLUTION_FEATURE_MAP_3840X2160_60FPS";
    private static final String BACK_UHD_120 =
            "BACK_CAMCORDER_RESOLUTION_FEATURE_MAP_3840X2160_120FPS";
    private static final String FRONT_UHD_120 =
            "FRONT_CAMCORDER_RESOLUTION_FEATURE_MAP_3840X2160_120FPS";

    /** Keys of the "CAMCORDER_RESOLUTION_FEATURE_MAP_*" entry (CameraResolution.FEATURE_INDEX_*). */
    private static final String FEATURE_SEAMLESS_ZOOM = "seamless-zoom-support";
    private static final String FEATURE_SUPPORTED_MODE = "supported-mode";
    private static final String FEATURE_VALUE = "value";
    private static final String MODE_SLOW_MOTION = "slow_motion";

    /** Returns the forced boolean for a device-feature tag, or null to leave the original value. */
    private Boolean forcedBooleanTag(String tag) {
        if (tag == null) return null;
        if ("SUPPORT_THERMISTOR_TEMPERATURE".equals(tag)) return Boolean.FALSE;
        if (mCamLogVideo) {
            for (String t : CAM_LOG_VIDEO_TAGS) {
                if (t.equals(tag)) return Boolean.TRUE;
            }
        }
        if (mCamLenses) {
            for (String t : CAM_LENS_TAGS) {
                if (t.equals(tag)) return Boolean.TRUE;
            }
        }
        if (mCamShutterMute && CAM_SHUTTER_MENU_TAG.equals(tag)) return Boolean.TRUE;
        return null;
    }

    /** Returns the forced float for a device-feature tag, or null to leave the original value. */
    private Float forcedFloatTag(String tag) {
        if (!mCamLenses || tag == null) return null;
        if (CAM_SECOND_TELE_ZOOM_TAG.equals(tag)) return 10.0f;
        if (CAM_TELE_ZOOM_TAG.equals(tag)) return 3.0f;
        return null;
    }

    private void logForcedTag(String tag, String orig, String value) {
        int n = mForcedTagCount++;
        if (n < 60) Log.i(TAG, "Forced camera feature " + tag + ": " + orig + " -> " + value);
    }

    private void hookCamera(ClassLoader cl) {
        Log.i(TAG, "Starting hookCamera...");

        // 1. Safe Resolution resolver to handle RESOLUTION_ strings and prevent crashes
        try {
            Class<?> resCls = cl.loadClass("com.sec.android.app.camera.interfaces.Resolution");
            hookAllMethods(resCls, "getResolution", chain -> {
                Object arg = chain.getArgs().isEmpty() ? null : chain.getArg(0);
                if (arg instanceof String) {
                    String s = (String) arg;
                    if (s.startsWith("RESOLUTION_")) {
                        s = s.substring(11).toLowerCase();
                    }
                    try {
                        Method valuesMethod = resCls.getMethod("values");
                        Method getStringMethod = resCls.getMethod("getString");
                        Object[] values = (Object[]) valuesMethod.invoke(null);
                        if (values != null) {
                            for (Object r : values) {
                                String str = (String) getStringMethod.invoke(r);
                                if (s.equalsIgnoreCase(str)) {
                                    return r;
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
                }
                return chain.proceed();
            });
        } catch (Throwable ignored) {
        }

        // 2. Camera 17+ (One UI 9) stores its device features in EnumMap<tag, value>, where the tag
        //    enum constant names are stable ("SUPPORT_LOG_VIDEO", ...) while all class names are
        //    obfuscated and change between builds (17.0.00.43 uses e5.d / e5.f / e5.g, older
        //    builds used P1.f / O1.f, ...). Hooking EnumMap.put therefore works for every version.
        mCamLogVideo = isPref("pref_camera_log_video", true);
        mCamLenses = isPref("pref_camera_4k60_lenses", true);
        mCamJpeg100 = isPref("pref_camera_jpeg_100", true);
        mCamShutterMute = isPref(PREF_CAM_SHUTTER_MUTE, true);
        mCam4k120SlowMo = isPref(PREF_CAM_4K120_SLOWMO, true);
        try {
            hookAllMethods(EnumMap.class, "put", chain -> {
                List<Object> args = chain.getArgs();
                if (args.size() >= 2) {
                    Object key = args.get(0);
                    Object value = args.get(1);
                    if (key instanceof Enum && value != null) {
                        String name = ((Enum<?>) key).name();
                        if (value instanceof Boolean) {
                            Boolean forced = forcedBooleanTag(name);
                            if (forced != null) {
                                logForcedTag(name, String.valueOf(value), String.valueOf(forced));
                                return chain.proceed(new Object[]{key, forced});
                            }
                        } else if (value instanceof Float) {
                            Float forced = forcedFloatTag(name);
                            if (forced != null) {
                                logForcedTag(name, String.valueOf(value), String.valueOf(forced));
                                return chain.proceed(new Object[]{key, forced});
                            }
                        }
                    }
                }
                return chain.proceed();
            });
            Log.i(TAG, "Hooked EnumMap.put for camera device features (logVideo=" + mCamLogVideo
                    + ", 4k60=" + mCamLenses + ")");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to hook EnumMap.put", t);
        }

        hookCameraResolutionMap(cl);
    }

    /**
     * Per-resolution camcorder feature map. Stock camera-feature.xml keeps
     * BACK_CAMCORDER_RESOLUTION_FEATURE_MAP_3840X2160_60FPS at seamless-zoom-support="false", and
     * Camera#isSeamlessZoomAvailable() returns exactly that flag while recording — which is why the
     * camera falls back to remosaic crop zoom and the lens buttons disappear in UHD 60 fps.
     * The 120 fps entry is likewise supported-mode="pro_video" only, so it never shows up in Slow
     * motion. Both are patched here instead of on disk (the map is what every getter reads).
     */
    private void hookCameraResolutionMap(ClassLoader cl) {
        Class<?> mapCls = null;
        for (String name : FEATURE_MAP_CLASS_CANDIDATES) {
            try {
                mapCls = cl.loadClass(name);
                Log.i(TAG, "Using camcorder feature map holder " + name);
                break;
            } catch (Throwable ignored) {
            }
        }
        if (mapCls == null) {
            Log.w(TAG, "Camcorder feature map holder not found, 4K60 lenses / 4K120 stay stock");
            return;
        }

        int count = 0;
        for (Method m : mapCls.getDeclaredMethods()) {
            Class<?>[] params = m.getParameterTypes();
            if (params.length != 1 || !params[0].isEnum()) continue;
            if (!java.util.Map.class.isAssignableFrom(m.getReturnType())) continue;
            try {
                hook(m).intercept(chain -> patchCamcorderFeature(chain, chain.proceed()));
                count++;
            } catch (Throwable t) {
                Log.w(TAG, "Failed to hook " + m.getName() + ": " + t.getMessage());
            }
        }
        Log.i(TAG, "Hooked " + count + " camcorder feature map getters on " + mapCls.getName()
                + " (4k60Lenses=" + mCamLenses + ", 4k120SlowMotion=" + mCam4k120SlowMo + ")");
    }

    /**
     * Returns a copy of the per-resolution feature map with our overrides applied. The original may
     * be shared/unmodifiable, so entries are copied into a plain HashMap; callers only read from it.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object patchCamcorderFeature(XposedInterface.Chain chain, Object original) {
        if (!(original instanceof java.util.Map)) return original;
        Object arg = chain.getArgs().isEmpty() ? null : chain.getArg(0);
        if (!(arg instanceof Enum)) return original;

        String tag = ((Enum<?>) arg).name();
        boolean wantLenses = mCamLenses && BACK_UHD_60.equals(tag);
        boolean wantSlowMo = mCam4k120SlowMo && (BACK_UHD_120.equals(tag) || FRONT_UHD_120.equals(tag));
        if (!wantLenses && !wantSlowMo) return original;

        java.util.Map src = (java.util.Map) original;
        Object modeObj = src.get(FEATURE_SUPPORTED_MODE);
        String mode = modeObj == null ? "" : modeObj.toString();
        boolean seamlessMissing = wantLenses && !"true".equals(String.valueOf(src.get(FEATURE_SEAMLESS_ZOOM)));
        boolean slowMoMissing = wantSlowMo && !mode.contains(MODE_SLOW_MOTION);
        if (!seamlessMissing && !slowMoMissing) return original;

        java.util.HashMap<Object, Object> patched = new java.util.HashMap<>();
        for (Object entryObj : src.entrySet()) {
            java.util.Map.Entry entry = (java.util.Map.Entry) entryObj;
            patched.put(entry.getKey(), entry.getValue());
        }
        if (seamlessMissing) {
            patched.put(FEATURE_SEAMLESS_ZOOM, "true");
        }
        if (slowMoMissing) {
            patched.put(FEATURE_SUPPORTED_MODE, mode.isEmpty() ? MODE_SLOW_MOTION : mode + "," + MODE_SLOW_MOTION);
            patched.put(FEATURE_VALUE, "true");
        }
        int n = mCamResolutionPatchCount++;
        if (n < 8) {
            Log.i(TAG, "Patched " + tag + " seamless-zoom=" + patched.get(FEATURE_SEAMLESS_ZOOM)
                    + " mode=" + patched.get(FEATURE_SUPPORTED_MODE));
        }
        return patched;
    }




    @SuppressWarnings({"unchecked", "rawtypes"})
    /**
     * 100% JPEG quality & Fallback prevention hooks
     */
    private void hookCameraJpeg(ClassLoader cl) {
        // Prevent optical fallback to digital crop (forces optical sensor 54 at 10x)
        try {
            Class<?> crbCls = CaptureRequest.Builder.class;
            hookAllMethods(crbCls, "build", chain -> {
                if (mCamLenses) {
                    try {
                        CaptureRequest.Builder b = (CaptureRequest.Builder) chain.getThisObject();
                        b.set(KEY_DUAL_CAMERA_DISABLE, Boolean.TRUE);
                        Long curPolicy = b.get(KEY_SPECIAL_IMAGE_QUALITY_POLICY);
                        b.set(KEY_SPECIAL_IMAGE_QUALITY_POLICY, (curPolicy != null ? curPolicy : 0L) | 2L);
                    } catch (Throwable ignored) {}
                }
                return chain.proceed();
            });
            Log.i(TAG, "Hooked CaptureRequest.Builder.build for dualCameraDisable & fallback prevention");
        } catch (Throwable t) {
            Log.w(TAG, "Failed to hook CaptureRequest.Builder.build: " + t.getMessage());
        }

        try {
            hookAllMethods(CaptureRequest.Builder.class, "set", chain -> {
                Object key = chain.getArg(0);
                if (key instanceof CaptureRequest.Key) {
                    String name = ((CaptureRequest.Key<?>) key).getName();
                    if (mCamLenses && "samsung.android.control.dualCameraDisable".equals(name)) {
                        return chain.proceed(new Object[]{key, Boolean.TRUE});
                    }
                    if (mCamLenses && "samsung.android.control.specialImageQualityPolicy".equals(name)) {
                        Object val = chain.getArg(1);
                        if (val instanceof Long) {
                            return chain.proceed(new Object[]{key, ((Long) val) | 2L});
                        }
                    }
                    if (mCamJpeg100 && CaptureRequest.JPEG_QUALITY.equals(key)) {
                        return chain.proceed(new Object[]{key, (byte) 100});
                    }
                }
                return chain.proceed();
            });
        } catch (Throwable ignored) {}

        try {
            hookAllMethods(Bitmap.class, "compress", chain -> {
                if (mCamJpeg100) {
                    Object format = chain.getArg(0);
                    if (Bitmap.CompressFormat.JPEG.equals(format)) {
                        Object[] args = chain.getArgs().toArray();
                        args[1] = 100;
                        return chain.proceed(args);
                    }
                }
                return chain.proceed();
            });
        } catch (Throwable ignored) {}
    }

    /**
     * Camera 17 (One UI 9) picture pipeline: every enhancement stage is a core2 NodeChain that
     * NodeControllerComposer switches on via NodeChain#enableNodeChain(true). A disabled chain
     * forwards its input unchanged, so not enabling it leaves the raw ISP/JPEG result only.
     */
    /**
     * Settings.Global mirror read with the hooked app's own uid. systemContext() belongs to the
     * "android" package, so reading Settings through it throws SecurityException in app processes.
     */
    private boolean isPrefAppCtx(String key, boolean defVal) {
        Context ctx = currentApp();
        if (ctx != null) {
            try {
                int v = Settings.Global.getInt(ctx.getContentResolver(), GLOBAL_PREFIX + key, -1);
                if (v >= 0) return v != 0;
            } catch (Throwable ignored) {
            }
        }
        return isPref(key, defVal);
    }

    /** Application object of the hooked process, or null outside an app process. */
    private static Context currentApp() {
        try {
            Object app = Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication").invoke(null);
            if (app instanceof Context) return (Context) app;
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** Camera-app prefs are writable by the Camera settings switch; they win over the mirror. */
    private SharedPreferences cameraPrefs(Context ctx) {
        return ctx.getSharedPreferences(ctx.getPackageName() + "_preferences", Context.MODE_PRIVATE);
    }

    private boolean isSkipPostProcEnabled() {
        Context ctx = currentApp();
        if (ctx != null) {
            try {
                SharedPreferences sp = cameraPrefs(ctx);
                Object raw = sp.getAll().get(CAM_UI_PREF_KEY);
                // Samsung's settings framework may store the value as a String.
                if (raw instanceof Boolean) return (Boolean) raw;
                if (raw != null) return "true".equals(String.valueOf(raw));
            } catch (Throwable ignored) {
            }
        }
        return isPrefAppCtx(PREF_CAM_SKIP_POSTPROC, false);
    }

    /**
     * Adds a switch to the Camera settings screen. The switch is non-persistent (androidx would
     * otherwise freeze the mirrored companion value into Camera prefs on first render), so the
     * change listener stores it explicitly and it becomes authoritative.
     */
    private void hookCameraSettingsToggle(ClassLoader cl) {
        try {
            Class<?> fragCls = cl.loadClass("sc.p1");
            hookAllMethods(fragCls, "onCreatePreferences", chain -> {
                Object result = chain.proceed();
                try {
                    Object fragment = chain.getThisObject();
                    Object screen = fragment.getClass().getMethod("getPreferenceScreen").invoke(fragment);
                    if (screen != null) addSkipPostProcPref(cl, fragment, screen);
                } catch (Throwable t) {
                    Log.w(TAG, "Skip-postproc switch injection failed: " + t);
                }
                return result;
            });
            // The Camera settings fragment assigns its own OnPreferenceClickListener to every row
            // after onCreatePreferences, so ours has to be re-attached later to win the field.
            hookAllMethods(fragCls, "onResume", chain -> {
                Object result = chain.proceed();
                try {
                    attachSkipPrefListener(cl, chain.getThisObject());
                } catch (Throwable ignored) {
                }
                return result;
            });
            Log.i(TAG, "Hooked Camera settings screen for skip-post-processing switch");
        } catch (Throwable t) {
            Log.w(TAG, "Camera settings screen not found: " + t.getMessage());
        }
    }

    private void addSkipPostProcPref(ClassLoader cl, Object fragment, Object screen) throws Throwable {
        Class<?> groupCls = cl.loadClass("androidx.preference.PreferenceGroup");
        if (groupCls.getMethod("findPreference", CharSequence.class).invoke(screen, CAM_UI_PREF_KEY) != null) return;

        Context ctx = (Context) cl.loadClass("androidx.fragment.app.Fragment")
                .getMethod("getContext").invoke(fragment);
        if (ctx == null) return;

        Class<?> prefCls = cl.loadClass("androidx.preference.SwitchPreferenceCompat");
        Object pref = prefCls.getConstructor(Context.class).newInstance(ctx);
        prefCls.getMethod("setKey", String.class).invoke(pref, CAM_UI_PREF_KEY);
        prefCls.getMethod("setTitle", CharSequence.class).invoke(pref, "Skip post-processing");
        prefCls.getMethod("setSummary", CharSequence.class).invoke(pref,
                "Photos bypass noise reduction, HDR, AI ISP and other Samsung processing. Relaunch Camera to apply.");
        prefCls.getMethod("setPersistent", boolean.class).invoke(pref, false);
        prefCls.getMethod("setChecked", boolean.class).invoke(pref, isSkipPostProcEnabled());

        Class<?> catCls = cl.loadClass("androidx.preference.PreferenceCategory");
        Object target = screen;
        int count = (Integer) groupCls.getMethod("getPreferenceCount").invoke(screen);
        for (int i = 0; i < count; i++) {
            Object p = groupCls.getMethod("getPreference", int.class).invoke(screen, i);
            if (catCls.isInstance(p)) { target = p; break; }
        }
        groupCls.getMethod("addPreference", cl.loadClass("androidx.preference.Preference"))
                .invoke(target, pref);
    }

    private void attachSkipPrefListener(ClassLoader cl, Object fragment) throws Throwable {
        Object screen = fragment.getClass().getMethod("getPreferenceScreen").invoke(fragment);
        if (screen == null) return;
        Class<?> groupCls = cl.loadClass("androidx.preference.PreferenceGroup");
        Object pref = groupCls.getMethod("findPreference", CharSequence.class).invoke(screen, CAM_UI_PREF_KEY);
        if (pref == null) return;
        Context ctx = (Context) cl.loadClass("androidx.fragment.app.Fragment")
                .getMethod("getContext").invoke(fragment);
        if (ctx == null) return;
        final Context appCtx = ctx.getApplicationContext() != null ? ctx.getApplicationContext() : ctx;
        Class<?> prefCls = cl.loadClass("androidx.preference.Preference");
        prefCls.getMethod("setOnPreferenceClickListener",
                cl.loadClass("androidx.preference.Preference$OnPreferenceClickListener"))
                .invoke(pref, java.lang.reflect.Proxy.newProxyInstance(cl,
                        new Class<?>[]{cl.loadClass("androidx.preference.Preference$OnPreferenceClickListener")},
                        (proxy, method, args) -> {
                            if (!"onPreferenceClick".equals(method.getName())) return false;
                            boolean next = !isSkipPostProcEnabled();
                            cameraPrefs(appCtx).edit().putBoolean(CAM_UI_PREF_KEY, next).apply();
                            Class<?> switchCls = cl.loadClass("androidx.preference.TwoStatePreference");
                            switchCls.getMethod("setChecked", boolean.class).invoke(pref, next);
                            Log.i(TAG, "Camera settings: skip post-processing = " + next);
                            return true;
                        }));
    }
    private void hookCameraPostProcessing(ClassLoader cl) {
        try {
            Class<?> chainCls = cl.loadClass("com.samsung.android.camera.core2.node.NodeChain");
            // NodeChain$Key is public; the runtime instances are package-private anonymous subclasses,
            // so getName must be resolved on the public type or invoke() throws IllegalAccessException.
            final Method getName = cl.loadClass("com.samsung.android.camera.core2.node.NodeChain$Key")
                    .getMethod("getName");
            final Method getKey = chainCls.getMethod("getKey");
            hookAllMethods(chainCls, "enableNodeChain", chain -> {
                if (!isSkipPostProcEnabled()) return chain.proceed();
                try {
                    Object key = getKey.invoke(chain.getThisObject());
                    Object name = key == null ? null : getName.invoke(key);

                    if (name instanceof String && POST_PROC_CHAINS.contains(name)) {
                        if (mLoggedSkippedChains.add((String) name)) {
                            Log.i(TAG, "Post-processing chain skipped: " + name);
                        }
                        return null; // do not enable -> chain passes data through
                    }
                } catch (Throwable ignored) {
                }
                return chain.proceed();
            });
            Log.i(TAG, "Hooked NodeChain.enableNodeChain for post-processing skip");
        } catch (Throwable t) {
            Log.w(TAG, "Failed to hook post-processing chains: " + t.getMessage());
        }
    }

    /**
     * System Server hooks (FLAG_SECURE -> screenshot bypass)
     */
    private void hookSystemServer(ClassLoader cl) {
        try {
            Class<?> windowStateClass = cl.loadClass("com.android.server.wm.WindowState");
            hookAllMethods(windowStateClass, "isSecureLocked", chain -> {
                if (isPref("pref_system_disable_flag_secure", true)) {
                    return false;
                }
                return chain.proceed();
            });
        } catch (Throwable ignored) {}

        try {
            Class<?> scBuilder = cl.loadClass("android.view.SurfaceControl$Builder");
            hookAllMethods(scBuilder, "setSecure", chain -> {
                if (isPref("pref_system_disable_flag_secure", true)) {
                    return chain.proceed(new Object[]{false});
                }
                return chain.proceed();
            });
        } catch (Throwable ignored) {}
    }

    /**
     * System Properties hooks (telemetry disable, 100% JPEG quality)
     */
    private void hookSystemProperties(ClassLoader cl) {
        try {
            Class<?> spCls = cl.loadClass("android.os.SystemProperties");
            hookAllMethods(spCls, "get", chain -> {
                String key = (String) chain.getArg(0);
                if (key != null) {
                    if (("profiler.force_disable_err_rpt".equals(key) || "profiler.force_disable_ulog".equals(key))
                            && isPref("pref_system_disable_telemetry", true)) {
                        return "1";
                    }
                    if ("ro.media.enc.jpeg.quality".equals(key) && isPref("pref_camera_jpeg_100", true)) {
                        return "100";
                    }
                }
                return chain.proceed();
            });

            hookAllMethods(spCls, "getInt", chain -> {
                String key = (String) chain.getArg(0);
                if (key != null) {
                    if (("profiler.force_disable_err_rpt".equals(key) || "profiler.force_disable_ulog".equals(key))
                            && isPref("pref_system_disable_telemetry", true)) {
                        return 1;
                    }
                    if ("ro.media.enc.jpeg.quality".equals(key) && isPref("pref_camera_jpeg_100", true)) {
                        return 100;
                    }                }
                return chain.proceed();
            });

            hookAllMethods(spCls, "getBoolean", chain -> {
                String key = (String) chain.getArg(0);
                if (key != null) {
                    if (("profiler.force_disable_err_rpt".equals(key) || "profiler.force_disable_ulog".equals(key))
                            && isPref("pref_system_disable_telemetry", true)) {
                        return true;
                    }
                }
                return chain.proceed();
            });
        } catch (Throwable ignored) {}
    }

    /**
     * Native Call Recording hook
     */
    private void hookCallRecording(ClassLoader cl) {
        try {
            Class<?> cscClass = cl.loadClass("com.samsung.android.feature.SemCscFeature");
            hookAllMethods(cscClass, "getString", chain -> {
                if ("CscFeature_VoiceCall_ConfigRecording".equals(chain.getArg(0))) {
                    if (isPref("pref_telephony_call_recording", true)) {
                        return "RecordingAllowed";
                    }
                }
                return chain.proceed();
            });
            Log.i(TAG, "Hooked SemCscFeature for Call Recording");
        } catch (Throwable ignored) {}
    }

    /**
     * Floating feature hook for EAD (Adaptive Color Tone / eyes comfort)
     */
    private void hookFloatingFeatures(ClassLoader cl) {
        try {
            Class<?> ffCls = cl.loadClass("com.samsung.android.feature.SemFloatingFeature");
            hookAllMethods(ffCls, "getBoolean", chain -> {
                String key = (String) chain.getArg(0);
                if (key != null) {
                    if (key.contains("ENVIRONMENT_ADAPTIVE") || key.contains("ADAPTIVE_COLOR_TONE")) {
                        if (isPref("pref_display_adaptive_color_tone", true)) return true;
                    }
                }
                return chain.proceed();
            });

            hookAllMethods(ffCls, "getString", chain -> {
                String key = (String) chain.getArg(0);
                if (key != null) {
                    if (key.contains("ENVIRONMENT_ADAPTIVE") || key.contains("ADAPTIVE_COLOR_TONE")) {
                        if (isPref("pref_display_adaptive_color_tone", true)) return "TRUE";
                    }
                }
                return chain.proceed();
            });

            hookAllMethods(ffCls, "getInt", chain -> {
                String key = (String) chain.getArg(0);
                if (key != null) {
                    if ("SEC_FLOATING_FEATURE_LCD_SUPPORT_BLUE_FILTER_ADAPTIVE_MODE".equals(key)) {
                        if (isPref("pref_display_adaptive_color_tone", true)) return 1;
                    }
                }
                return chain.proceed();
            });
        } catch (Throwable ignored) {}
    }

    /**
     * CSC Features hook
     */
    private void hookCscFeatures(ClassLoader cl) {
        try {
            Class<?> cscCls = cl.loadClass("com.samsung.android.feature.SemCscFeature");
            hookAllMethods(cscCls, "getString", chain -> {
                String key = (String) chain.getArg(0);
                if ("CscFeature_VoiceCall_ConfigRecording".equals(key) && isPref("pref_telephony_call_recording", true)) {
                    return "RecordingAllowed";
                }
                if ("CscFeature_Common_SupportAllCountryIso".equals(key)) {
                    return "TRUE";
                }
                return chain.proceed();
            });
        } catch (Throwable ignored) {}
    }

    /**
     * Battery info hooks - expose the "EU regulatory" battery details that Samsung ships only
     * on newer models: battery health (BSOH), cycle count, first-use date, manufacture date
     * and the rated/typical capacity block.
     *
     * Gate: BatteryInfoUtils.isAvailableBatteryRegulatory(Context) returns false on the S23
     * (Rune.FEATURE_BATTERY_INFO_EU_REGULATORY* are all false and the battery IC check
     * BatteryManager.semGetValueAsBoolean(106) is false), so every controller extending
     * BatteryRegulatoryPreferenceController is GONE.
     *
     * Data sources (private EFS/sysfs nodes, read by the controllers themselves):
     *   /efs/FactoryApp/batt_discharge_level  -> cycles (value / 100)
     *   /efs/FactoryApp/batt_beginning_date   -> first use date (yyyymmdd)
     *   /efs/FactoryApp/HwParamBattQR         -> manufacture date
     *   /sys/class/power_supply/battery/fg_asoc -> BSOH percentage
     * The node reads fail for an unprivileged uid, so the controllers simply show nothing -
     * the rows are still listed, which is what "as on newer models" means visually.
     */
    private void hookBatteryInfo(ClassLoader cl) {
        if (!isPref(PREF_BATTERY_INFO, true)) {
            return;
        }
        // 1. The single gate used by every regulatory controller.
        try {
            Class<?> utils = cl.loadClass(
                    "com.samsung.android.settings.deviceinfo.batteryinfo.BatteryInfoUtils");
            hookAllMethods(utils, "isAvailableBatteryRegulatory", chain -> true);
            Log.i(TAG, "Battery info: isAvailableBatteryRegulatory() -> true");
        } catch (Throwable t) {
            Log.w(TAG, "Battery info: BatteryInfoUtils not found: " + t.getMessage());
        }

        // 2. Safety net: the base class and each concrete controller.
        String base = "com.samsung.android.settings.deviceinfo.batteryinfo.";
        String[] classes = new String[]{
                base + "BatteryRegulatoryPreferenceController",
                base + "BatteryHealthPreferenceController",
                base + "BatteryInfoCapacityPreferenceController",
                base + "BatteryInfoCapacityTypicalDescPreferenceController",
                base + "SecBatteryCycleCountPreferenceController",
                base + "SecBatteryFirstUseDatePreferenceController",
                base + "SecBatteryManufactureDatePreferenceController"
        };
        int hooked = 0;
        for (String name : classes) {
            try {
                Class<?> cls = cl.loadClass(name);
                hookAllMethods(cls, "getAvailabilityStatus", chain -> 0);
                hookAllMethods(cls, "supportBatteryCapacity", chain -> true);
                hooked++;
            } catch (Throwable ignored) {
            }
        }
        Log.i(TAG, "Battery info: unlocked " + hooked + " battery-info controllers");
    }

    /**
     * Camera shutter sound mute.
     *
     * Camera 17 gates the sound in two places:
     *  1. SUPPORT_SHUTTER_SOUND_MENU - makes the "Shutter sound" preference visible
     *     (forced through the EnumMap.put hook in hookCamera).
     *  2. CameraAudioManager (a8.v) sets mForceShutterSound on resume:
     *       Settings.System.getInt(resolver, "csc_pref_camera_forced_shuttersound_key", 0) == 1
     *       || IS_COUNTRY_JAPAN || IS_COUNTRY_KOREA
     *     and isPlaySoundAvailable() then returns false when the SHUTTER_SOUND setting is 0.
     *     Forcing mForceShutterSound to false (or the CSC setting to 0) enables the mute.
     */
    private void hookCameraShutterSound(ClassLoader cl) {
        if (!isPref(PREF_CAM_SHUTTER_MUTE, true)) {
            return;
        }

        // B. Intercept the CSC shutter-sound flag at the Settings.System level for the
        //    camera process only (this hook is installed in the camera ClassLoader scope).
        try {
            Class<?> settingsSystem = cl.loadClass("android.provider.Settings$System");
            for (Method m : settingsSystem.getDeclaredMethods()) {
                if (!"getInt".equals(m.getName())) continue;
                Class<?>[] t = m.getParameterTypes();
                if (t.length != 3 || t[0] != android.content.ContentResolver.class
                        || t[1] != String.class || t[2] != int.class) continue;
                hook(m).intercept(chain -> {
                    Object key = chain.getArg(1);
                    if (key instanceof String
                            && "csc_pref_camera_forced_shuttersound_key".equals(key)) {
                        Log.i(TAG, "Shutter sound: CSC force flag -> 0");
                        return 0;
                    }
                    return chain.proceed();
                });
            }
            Log.i(TAG, "Hooked Settings.System.getInt for shutter sound CSC flag");
        } catch (Throwable t) {
            Log.w(TAG, "Shutter sound: Settings.System hook failed: " + t.getMessage());
        }

        // C. Belt and braces: force the field on CameraAudioManager whatever the gate did.
        String[] audioManagers = new String[]{"a8.v", "com.sec.android.app.camera.CameraAudioManager"};
        for (String name : audioManagers) {
            try {
                Class<?> cls = cl.loadClass(name);
                hookAllMethods(cls, "onResume", chain -> {
                    Object r = chain.proceed();
                    try {
                        for (Field f : cls.getDeclaredFields()) {
                            if (!f.getType().equals(boolean.class)) continue;
                            String fn = f.getName();
                            if (!"p".equals(fn) && !fn.toLowerCase(Locale.ROOT).contains("forceshutter")) continue;
                            f.setAccessible(true);
                            f.setBoolean(chain.getThisObject(), false);
                        }
                    } catch (Throwable ignored) {
                    }
                    return r;
                });
                Log.i(TAG, "Shutter sound: cleared force flag in " + name);
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Restores the "swipe from the bottom corner to open the assistant" gesture.
     *
     * SystemUI gates it in NavBarHelper.updateAssistantAvailability():
     *     mAssistantAvailable = assistantComponent != null
     *         && mAssistantTouchGestureEnabled            (Settings.Secure assist_touch_gesture_enabled)
     *         && !mLongPressHomeEnabled                    (assist_long_press_home_enabled)
     *         && (BasicRune.SUPPORT_AI_AGENT
     *             ? (getNavigationBarSPluginFlags() & FLAG_NAVSTAR_ASSISTANT_ENABLED) == 0
     *             : true);
     * CornerGestureHandler.updateIsEnabled() then only attaches its input monitor when
     * mAssistantAvailable is true, and its onTouchEvent additionally checks
     * QuickStepContract.isAssistantGestureDisabled(sysUiFlags).
     *
     * On the S23 the assistant component resolves fine and the touch gesture is enabled, but
     * the chain still ends with mAssistantAvailable=false, so the corner handler never gets
     * an InputMonitor. We force the three gates open and also force allowGesture on the
     * handler itself.
     */
    /**
     * Samsung Settings hooks:
     * 1. Unlocks EAD (Adaptive Color Tone) in Settings > Display.
     * 2. Unlocks AMOLED Cinema (0), AMOLED Photo (1), and Basic (2) in Settings > Display > Screen Mode.
     * 3. Unlocks 30 minutes, 1 hour, and Never (Nikdy) in Settings > Display > Screen Timeout.
     * 4. Enables custom Time to take action (Accessibility UI timeout).
     */
    private void hookSettings(ClassLoader cl) {
        // 0. Battery info details (health / cycles / dates / capacity)
        hookBatteryInfo(cl);

        // 1. EAD (Adaptive Color Tone)
        try {
            Class<?> eadCls = cl.loadClass("com.samsung.android.settings.display.controller.SecEADPreferenceController");
            hookAllMethods(eadCls, "getAvailabilityStatus", chain -> {
                if (isPref("pref_display_adaptive_color_tone", true)) {
                    return 0; // AVAILABLE
                }
                return chain.proceed();
            });
            Log.i(TAG, "Hooked SecEADPreferenceController in Settings");
        } catch (Throwable t) {
            Log.w(TAG, "Failed to hook SecEADPreferenceController: " + t.getMessage());
        }

        // 2. Unrestricted fonts
        try {
            Class<?> fontUtils = cl.loadClass("com.samsung.android.settings.display.SecDisplayUtils");
            hookAllMethods(fontUtils, "isInvalidFont", chain -> {
                if (isPref("pref_display_unlock_fonts", true)) return false;
                return chain.proceed();
            });
        } catch (Throwable ignored) {}

        // 3. Display Screen Modes: Inject Cinema AMOLED (0), Photo AMOLED (1), Basic (2) into NewModePreview
        try {
            Class<?> adapterCls = cl.loadClass("com.samsung.android.settings.display.NewModePreview$ScreenModeAdapter");
            for (Constructor<?> ctor : adapterCls.getDeclaredConstructors()) {
                try {
                    hook(ctor).intercept(chain -> {
                        if (!isPref("pref_display_screen_mode", true)) return chain.proceed();
                        try {
                            Context ctx = null;
                            Object screenModeItems = null;
                            for (Object arg : chain.getArgs()) {
                                if (arg instanceof Context) {
                                    ctx = (Context) arg;
                                } else if (arg != null && arg.getClass().getName().contains("ScreenModeItems")) {
                                    screenModeItems = arg;
                                }
                            }

                            if (ctx != null && screenModeItems != null) {
                                Field fDb = screenModeItems.getClass().getDeclaredField("mDBValue");
                                fDb.setAccessible(true);
                                List dbValues = (List) fDb.get(screenModeItems);

                                Field fName = screenModeItems.getClass().getDeclaredField("mNameStringId");
                                fName.setAccessible(true);
                                List nameIds = (List) fName.get(screenModeItems);

                                int resCinema = ctx.getResources().getIdentifier("sec_screen_mode_premium_movie", "string", ctx.getPackageName());
                                if (resCinema == 0) {
                                    resCinema = ctx.getResources().getIdentifier("sec_screen_mode_movie", "string", ctx.getPackageName());
                                }

                                int resPhoto = ctx.getResources().getIdentifier("sec_screen_mode_amoled_photo", "string", ctx.getPackageName());
                                if (resPhoto == 0) {
                                    resPhoto = ctx.getResources().getIdentifier("sec_screen_mode_photo", "string", ctx.getPackageName());
                                }

                                int resBasic = ctx.getResources().getIdentifier("sec_screen_mode_basic", "string", ctx.getPackageName());

                                if (resCinema != 0 && !dbValues.contains(0)) {
                                    nameIds.add(resCinema);
                                    dbValues.add(0);
                                }
                                if (resPhoto != 0 && !dbValues.contains(1)) {
                                    nameIds.add(resPhoto);
                                    dbValues.add(1);
                                }
                                if (resBasic != 0 && !dbValues.contains(2)) {
                                    nameIds.add(resBasic);
                                    dbValues.add(2);
                                }
                                Log.i(TAG, "Successfully injected AMOLED Cinema (0), Photo (1), and Basic (2) into ScreenModeItems");
                            }
                        } catch (Throwable t) {
                            Log.w(TAG, "Error injecting display modes into ScreenModeAdapter", t);
                        }
                        return chain.proceed();
                    });
                    Log.i(TAG, "Hooked NewModePreview$ScreenModeAdapter constructor");
                } catch (Throwable t) {
                    Log.w(TAG, "Failed to hook ScreenModeAdapter constructor: " + t.getMessage());
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed to load ScreenModeAdapter: " + t.getMessage());
        }

        // 4. Screen Off Timeout: Add 30 minutes, 1 hour, and Never (Nikdy)
        try {
            Class<?> displayUtils = cl.loadClass("com.samsung.android.settings.display.SecDisplayUtils");
            hookAllMethods(displayUtils, "getScreenTimeoutEntryandValue", chain -> {
                if (!isPref("pref_display_screen_timeout", true)) return chain.proceed();
                CharSequence[] original = (CharSequence[]) chain.proceed();
                if (original == null) return null;

                try {
                    int type = ((Number) chain.getArg(2)).intValue();
                    Context ctx = (Context) chain.getArg(0);
                    boolean isCs = isCzech(ctx);

                    List<CharSequence> list = new ArrayList<>(Arrays.asList(original));

                    if (type == 1) { // Entries
                        boolean hasNever = false;
                        for (CharSequence cs : list) {
                            String s = cs.toString().toLowerCase();
                            if (s.contains("nikdy") || s.contains("never")) {
                                hasNever = true;
                                break;
                            }
                        }
                        if (!hasNever) {
                            list.add(isCs ? "30 minut" : "30 minutes");
                            list.add(isCs ? "1 hodina" : "1 hour");
                            list.add(isCs ? "Nikdy" : "Never");
                        }
                    } else if (type == 2) { // Values
                        boolean hasMax = false;
                        for (CharSequence cs : list) {
                            if ("2147483647".equals(cs.toString())) {
                                hasMax = true;
                                break;
                            }
                        }
                        if (!hasMax) {
                            list.add("1800000"); // 30m
                            list.add("3600000"); // 1h
                            list.add("2147483647"); // Never
                        }
                    }
                    return list.toArray(new CharSequence[0]);
                } catch (Throwable t) {
                    Log.w(TAG, "Error modifying screen timeout entries/values", t);
                    return original;
                }
            });

            hookAllMethods(displayUtils, "getTimeoutDescription", chain -> {
                if (!isPref("pref_display_screen_timeout", true)) return chain.proceed();
                try {
                    long timeout = -1L;
                    boolean hasTimeout = false;
                    Context descCtx = null;
                    for (Object a : chain.getArgs()) {
                        if (a instanceof Number && !hasTimeout) {
                            timeout = ((Number) a).longValue();
                            hasTimeout = true;
                        } else if (a instanceof Context && descCtx == null) {
                            descCtx = (Context) a;
                        }
                    }
                    if (hasTimeout && (timeout >= 2147483640L || timeout < 0)) {
                        return isCzech(descCtx) ? "Nikdy" : "Never";
                    }
                } catch (Throwable ignored) {}
                return chain.proceed();
            });
            Log.i(TAG, "Hooked SecDisplayUtils screen timeout methods");
        } catch (Throwable t) {
            Log.w(TAG, "Failed to hook SecDisplayUtils: " + t.getMessage());
        }

        // 5. Accessibility interactive UI timeout: Support custom timeout values in Settings
        try {
            Class<?> a11yFragmentCls = cl.loadClass("com.samsung.android.settings.accessibility.dexterity.SecAccessibilityControlTimeoutPreferenceFragment");
            hookAllMethods(a11yFragmentCls, "onCreatePreferences", chain -> {
                if (!isPref("pref_display_action_timeout", true)) return chain.proceed();
                Object result = chain.proceed();
                try {
                    Object fragment = chain.getThisObject();
                    Method getPrefScreenMethod = fragment.getClass().getMethod("getPreferenceScreen");
                    Object prefScreen = getPrefScreenMethod.invoke(fragment);
                    Method getContextMethod = fragment.getClass().getMethod("getContext");
                    Context ctx = (Context) getContextMethod.invoke(fragment);

                    if (prefScreen != null && ctx != null) {
                        int currentVal = Settings.Secure.getInt(ctx.getContentResolver(), "accessibility_interactive_ui_timeout_ms", 0);
                        if (currentVal > 0 && currentVal != 10000 && currentVal != 30000 && currentVal != 60000 && currentVal != 120000) {
                            // Current value is custom! Add preference for it
                            Class<?> prefCls = cl.loadClass("com.samsung.android.settings.accessibility.base.widget.AccessibilitySelectorWithWidgetPreference");
                            Constructor<?> prefCtor = prefCls.getConstructor(Context.class);
                            Object customPref = prefCtor.newInstance(ctx);

                            Method setKey = prefCls.getMethod("setKey", String.class);
                            setKey.invoke(customPref, "accessibility_control_timeout_custom_" + currentVal);

                            Class<?> utilCls = cl.loadClass("com.samsung.android.settings.accessibility.dexterity.SecAccessibilityTimeoutUtil");
                            Method getTitle = utilCls.getMethod("getTimeoutTitle", Context.class, int.class);
                            CharSequence title = (CharSequence) getTitle.invoke(null, ctx, currentVal);

                            Method setTitle = prefCls.getMethod("setTitle", CharSequence.class);
                            setTitle.invoke(customPref, title);

                            Method setChecked = prefCls.getMethod("setChecked", boolean.class);
                            setChecked.invoke(customPref, true);

                            Method addPref = prefScreen.getClass().getMethod("addPreference", cl.loadClass("androidx.preference.Preference"));
                            addPref.invoke(prefScreen, customPref);
                            Log.i(TAG, "Added custom accessibility timeout preference: " + title);
                        }
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "Error adding custom accessibility timeout preference", t);
                }
                return result;
            });
            Log.i(TAG, "Hooked SecAccessibilityControlTimeoutPreferenceFragment");
        } catch (Throwable t) {
            Log.w(TAG, "Failed to hook SecAccessibilityControlTimeoutPreferenceFragment: " + t.getMessage());
        }

        // 6. Outdoor Mode in Settings > Display
        try {
            Class<?> outdoorCls = cl.loadClass("com.samsung.android.settings.display.controller.SecOutDoorModePreferenceController");
            hookAllMethods(outdoorCls, "isAvailable", chain -> {
                if (isPref("pref_display_outdoor_mode", true)) {
                    return true;
                }
                return chain.proceed();
            });
            hookAllMethods(outdoorCls, "getAvailabilityStatus", chain -> {
                if (isPref("pref_display_outdoor_mode", true)) {
                    return 0; // AVAILABLE
                }
                return chain.proceed();
            });
            Log.i(TAG, "Hooked SecOutDoorModePreferenceController in Settings");
        } catch (Throwable t) {
            Log.w(TAG, "Failed to hook SecOutDoorModePreferenceController: " + t.getMessage());
        }

        try {
            Class<?> runeCls = cl.loadClass("com.samsung.android.settings.Rune");
            hookAllMethods(runeCls, "supportOutdoorMode", chain -> {
                if (isPref("pref_display_outdoor_mode", true)) {
                    return true;
                }
                return chain.proceed();
            });
            Log.i(TAG, "Hooked Rune.supportOutdoorMode in Settings");
        } catch (Throwable ignored) {}

        // 7. ContentProvider hook in Settings: allows S23 Enhancer app and QS Tile to set display_outdoor_mode as UID 1000
        try {
            Class<?> cpCls = android.content.ContentProvider.class;
            hookAllMethods(cpCls, "call", chain -> {
                List<Object> args = chain.getArgs();
                String m0 = args.size() > 0 && args.get(0) != null ? args.get(0).toString() : "";
                String m1 = args.size() > 1 && args.get(1) != null ? args.get(1).toString() : "";
                if ("set_outdoor_mode".equals(m0) || "set_outdoor_mode".equals(m1)) {
                    android.content.ContentProvider cp = (android.content.ContentProvider) chain.getThisObject();
                    if (cp == null) return chain.proceed();
                    String callingPkg = cp.getCallingPackage();
                    int callingUid = android.os.Binder.getCallingUid();
                    if (!"com.s23.enhancements".equals(callingPkg)
                            && callingUid != Process.SYSTEM_UID
                            && callingUid != 0) {
                        Log.w(TAG, "Blocked unauthorized set_outdoor_mode from pkg=" + callingPkg + " uid=" + callingUid);
                        return chain.proceed();
                    }
                    String valStr = "set_outdoor_mode".equals(m0)
                        ? (args.size() > 1 && args.get(1) != null ? args.get(1).toString() : "0")
                        : (args.size() > 2 && args.get(2) != null ? args.get(2).toString() : "0");
                    int val = "1".equals(valStr) ? 1 : 0;
                    Context ctx = cp.getContext();
                    if (ctx != null) {
                        Settings.System.putInt(ctx.getContentResolver(), "display_outdoor_mode", val);
                        Log.i(TAG, "ContentProvider.call: set display_outdoor_mode to " + val + " (UID 1000)");
                        android.os.Bundle res = new android.os.Bundle();
                        res.putBoolean("success", true);
                        res.putInt("value", val);
                        return res;
                    }
                }
                return chain.proceed();
            });
            Log.i(TAG, "Hooked ContentProvider.call in Settings");
        } catch (Throwable t) {
            Log.w(TAG, "Failed to hook ContentProvider.call: " + t.getMessage());
        }

        // 8. Wi-Fi passwords: skip the lock-screen verification gate and reveal saved password
        try {
            Class<?> wifiUtils = cl.loadClass("com.android.settings.wifi.WifiUtils");
            hookAllMethods(wifiUtils, "isSupportVerifyingForPassword", chain -> {
                if (isPref("pref_system_show_wifi_passwords", true)) {
                    return false;
                }
                return chain.proceed();
            });
            Log.i(TAG, "Hooked WifiUtils.isSupportVerifyingForPassword");
        } catch (Throwable ignored) {}

        try {
            Class<?> detailsCls = cl.loadClass("com.samsung.android.settings.wifi.details.WifiDetailPreferenceController");
            hookAllMethods(detailsCls, "displayPreference", chain -> {
                Object result = chain.proceed();
                if (!isPref("pref_system_show_wifi_passwords", true)) return result;
                try {
                    Object self = chain.getThisObject();

                    Field pv = detailsCls.getDeclaredField("mPasswordView");
                    pv.setAccessible(true);
                    Object view = pv.get(self);
                    if (view instanceof android.widget.EditText) {
                        ((android.widget.EditText) view).setInputType(
                                android.text.InputType.TYPE_CLASS_TEXT
                                        | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                    }

                    Field pb = detailsCls.getDeclaredField("mPasswordShowButton");
                    pb.setAccessible(true);
                    Object btn = pb.get(self);
                    if (btn instanceof View) {
                        ((View) btn).setVisibility(View.GONE);
                    }
                    Log.i(TAG, "Wi-Fi password revealed in network details");
                } catch (Throwable t) {
                    Log.w(TAG, "Could not reveal Wi-Fi password: " + t.getMessage());
                }
                return result;
            });
            Log.i(TAG, "Hooked WifiDetailPreferenceController (password reveal)");
        } catch (Throwable ignored) {}

        hookAppInfoDetails(cl);
        hookFingerprintImproveAccuracy(cl);
    }

    /**
     * Settings -> Security and privacy -> Fingerprints: keeps "Improve accuracy" (R.id.update)
     * in each fingerprint's popup menu (stock Settings hides it when DEVICE_INITIAL_SDK_INT < 36).
     */
    private void hookFingerprintImproveAccuracy(ClassLoader cl) {
        try {
            Class<?> fpSettingsCls = cl.loadClass(
                    "com.samsung.android.settings.biometrics.fingerprint.FingerprintSettings");
            Class<?> menuBuilderCls = cl.loadClass("androidx.appcompat.view.menu.MenuBuilder");

            hookAllMethods(fpSettingsCls, "onPreferenceTreeClick", chain -> {
                if (mFpUpdateMenuId <= 0) {
                    Object ctxObj = invokeMethod(chain.getThisObject(), "getContext");
                    if (ctxObj instanceof Context) {
                        resolveFpUpdateMenuId((Context) ctxObj);
                    }
                }
                sInFpOptionMenu.set(Boolean.TRUE);
                try {
                    return chain.proceed();
                } finally {
                    sInFpOptionMenu.remove();
                }
            });

            Method removeItem = menuBuilderCls.getDeclaredMethod("removeItem", int.class);
            hook(removeItem).intercept(chain -> {
                if (Boolean.TRUE.equals(sInFpOptionMenu.get())
                        && isPref(PREF_FP_IMPROVE_ACCURACY, true)
                        && !chain.getArgs().isEmpty()
                        && chain.getArg(0) instanceof Integer) {
                    int id = (Integer) chain.getArg(0);
                    Object mCtxObj = getFieldValue(chain.getThisObject(), "mContext");
                    Context ctx = mCtxObj instanceof Context ? (Context) mCtxObj : null;
                    int updateId = mFpUpdateMenuId;
                    if (updateId <= 0 && ctx != null) {
                        updateId = resolveFpUpdateMenuId(ctx);
                    }
                    boolean isUpdateItem = (updateId > 0 && id == updateId);
                    if (!isUpdateItem && ctx != null) {
                        try {
                            isUpdateItem = "update".equals(ctx.getResources().getResourceEntryName(id));
                        } catch (Throwable ignored) {
                        }
                    }
                    if (isUpdateItem) {
                        Log.i(TAG, "Unlocked Fingerprint Improve accuracy option (kept menu id=0x"
                                + Integer.toHexString(id) + ")");
                        return null;
                    }
                }
                return chain.proceed();
            });
            Log.i(TAG, "Hooked FingerprintSettings + MenuBuilder for Improve accuracy");
        } catch (Throwable t) {
            Log.w(TAG, "Failed to hook FingerprintSettings Improve accuracy: " + t.getMessage());
        }
    }

    private int resolveFpUpdateMenuId(Context ctx) {
        if (ctx == null) return 0;
        int resolved = ctx.getResources().getIdentifier("update", "id", ctx.getPackageName());
        if (resolved == 0) {
            resolved = ctx.getResources().getIdentifier("update", "id", "com.android.settings");
        }
        if (resolved > 0) {
            mFpUpdateMenuId = resolved;
        }
        return resolved;
    }

    /**
     * Settings App Info: injects Package name (tap to copy) and Install/Update date rows
     * right below App version in AppInfoDashboardFragment.
     */
    private void hookAppInfoDetails(ClassLoader cl) {
        try {
            Class<?> verCls = cl.loadClass("com.android.settings.applications.appinfo.AppVersionPreferenceController");
            hookAllMethods(verCls, "updateState", chain -> {
                Object res = chain.proceed();
                Object self = chain.getThisObject();
                Object pref = chain.getArgs().isEmpty() ? null : chain.getArg(0);
                if (self == null || pref == null) return res;

                try {
                    Object parentFrag = getFieldValue(self, "mParent");
                    Object pkgInfoObj = getFieldValue(parentFrag, "mPackageInfo");
                    Object parentGroup = invokeMethod(pref, "getParent");
                    Object ctxObj = invokeMethod(pref, "getContext");
                    if (parentGroup == null || !(ctxObj instanceof Context)) return res;

                    Context ctx = (Context) ctxObj;
                    Method findPref = parentGroup.getClass().getMethod("findPreference", CharSequence.class);
                    Object pkgPref = findPref.invoke(parentGroup, "s23_appinfo_package_name");
                    Object datePref = findPref.invoke(parentGroup, "s23_appinfo_install_date");

                    if (!isPref(PREF_APP_INFO_DETAILS, true)
                            || !(pkgInfoObj instanceof android.content.pm.PackageInfo)) {
                        setPrefVisible(pkgPref, false);
                        setPrefVisible(datePref, false);
                        return res;
                    }

                    android.content.pm.PackageInfo pkgInfo = (android.content.pm.PackageInfo) pkgInfoObj;
                    String pkgName = pkgInfo.packageName;
                    if (pkgName == null || pkgName.isEmpty()) return res;

                    int baseOrder = 0;
                    Object ordObj = invokeMethod(pref, "getOrder");
                    if (ordObj instanceof Integer) baseOrder = (Integer) ordObj;
                    boolean isCs = isCzech(ctx);

                    Method addPref = parentGroup.getClass().getMethod("addPreference", cl.loadClass("androidx.preference.Preference"));

                    boolean createdPkgPref = false;
                    if (pkgPref == null) {
                        pkgPref = newSettingsPreference(cl, pref, ctx);
                        if (pkgPref != null) {
                            invokeArg(pkgPref, "setKey", String.class, "s23_appinfo_package_name");
                            invokeArg(pkgPref, "setPersistent", boolean.class, false);
                            invokeArg(pkgPref, "setIconSpaceReserved", boolean.class, false);
                            invokeArg(pkgPref, "setOrder", int.class, baseOrder + 1);
                            invokeArg(pkgPref, "setSelectable", boolean.class, true);
                            addPref.invoke(parentGroup, pkgPref);
                            createdPkgPref = true;
                        }
                    }
                    boolean pkgChanged = createdPkgPref;
                    if (pkgPref != null) {
                        Object prevSummary = invokeMethod(pkgPref, "getSummary");
                        if (!pkgName.equals(prevSummary != null ? prevSummary.toString() : null)) {
                            pkgChanged = true;
                        }
                        invokeArg(pkgPref, "setTitle", CharSequence.class,
                                isCs ? "N\u00E1zev bal\u00ED\u010Dku" : "Package name");
                        invokeArg(pkgPref, "setSummary", CharSequence.class, pkgName);
                        setPrefVisible(pkgPref, true);

                        if (pkgChanged) {
                            try {
                                Class<?> listenerCls = cl.loadClass("androidx.preference.Preference$OnPreferenceClickListener");
                                Object proxy = java.lang.reflect.Proxy.newProxyInstance(
                                        cl,
                                        new Class<?>[]{listenerCls},
                                        (pObj, method, args) -> {
                                            if ("onPreferenceClick".equals(method.getName())) {
                                                try {
                                                    android.content.ClipboardManager cm =
                                                            (android.content.ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                                                    if (cm != null) {
                                                        boolean toastCs = isCzech(ctx);
                                                        cm.setPrimaryClip(android.content.ClipData.newPlainText("package", pkgName));
                                                        android.widget.Toast.makeText(
                                                                ctx,
                                                                toastCs ? "N\u00E1zev bal\u00ED\u010Dku zkop\u00EDrov\u00E1n" : "Package name copied",
                                                                android.widget.Toast.LENGTH_SHORT).show();
                                                    }
                                                } catch (Throwable ignored) {
                                                }
                                                return Boolean.TRUE;
                                            }
                                            return null;
                                        });
                                pkgPref.getClass().getMethod("setOnPreferenceClickListener", listenerCls)
                                        .invoke(pkgPref, proxy);
                            } catch (Throwable ignored) {
                            }
                        }
                    }

                    if (datePref == null) {
                        datePref = newSettingsPreference(cl, pref, ctx);
                        if (datePref != null) {
                            invokeArg(datePref, "setKey", String.class, "s23_appinfo_install_date");
                            invokeArg(datePref, "setPersistent", boolean.class, false);
                            invokeArg(datePref, "setIconSpaceReserved", boolean.class, false);
                            invokeArg(datePref, "setOrder", int.class, baseOrder + 2);
                            invokeArg(datePref, "setSelectable", boolean.class, false);
                            addPref.invoke(parentGroup, datePref);
                        }
                    }
                    if (datePref != null) {
                        String installStr = formatAppTimestamp(ctx, pkgInfo.firstInstallTime);
                        StringBuilder summary = new StringBuilder(installStr);
                        if (pkgInfo.lastUpdateTime > pkgInfo.firstInstallTime + 60_000L) {
                            summary.append('\n')
                                   .append(isCs ? "Aktualizov\u00E1no: " : "Updated: ")
                                   .append(formatAppTimestamp(ctx, pkgInfo.lastUpdateTime));
                        }
                        invokeArg(datePref, "setTitle", CharSequence.class,
                                isCs ? "Datum instalace" : "Install date");
                        invokeArg(datePref, "setSummary", CharSequence.class, summary.toString());
                        setPrefVisible(datePref, true);
                    }
                    if (pkgChanged) {
                        Log.i(TAG, "Injected App Info details for " + pkgName);
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "AppInfo details inject failed: " + t.getMessage());
                }
                return res;
            });
            Log.i(TAG, "Hooked AppVersionPreferenceController for App Info details");
        } catch (Throwable t) {
            Log.w(TAG, "Failed to hook AppVersionPreferenceController: " + t.getMessage());
        }
    }

    private static Object newSettingsPreference(ClassLoader cl, Object template, Context ctx) {
        try {
            return cl.loadClass("androidx.preference.Preference")
                    .getConstructor(Context.class).newInstance(ctx);
        } catch (Throwable ignored) {
        }
        try {
            return template.getClass().getConstructor(Context.class).newInstance(ctx);
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String formatAppTimestamp(Context ctx, long timeMs) {
        if (timeMs <= 0L) return "-";
        java.util.Date d = new java.util.Date(timeMs);
        return android.text.format.DateFormat.getMediumDateFormat(ctx).format(d)
                + " " + android.text.format.DateFormat.getTimeFormat(ctx).format(d);
    }

    static boolean isCzech(Context ctx) {
        if (ctx != null) {
            try {
                Locale loc = ctx.getResources().getConfiguration().getLocales().get(0);
                if (loc != null) {
                    return "cs".equalsIgnoreCase(loc.getLanguage());
                }
            } catch (Throwable ignored) {
            }
        }
        return "cs".equalsIgnoreCase(Locale.getDefault().getLanguage());
    }

    private static Object getFieldValue(Object target, String name) {
        if (target == null) return null;
        for (Class<?> c = target.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException ignored) {
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    private static Object invokeMethod(Object target, String name) {
        if (target == null) return null;
        try {
            return target.getClass().getMethod(name).invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void invokeArg(Object target, String name, Class<?> argType, Object arg) {
        if (target == null) return;
        try {
            target.getClass().getMethod(name, argType).invoke(target, arg);
        } catch (Throwable ignored) {
        }
    }

    private static void setPrefVisible(Object pref, boolean visible) {
        if (pref != null) {
            invokeArg(pref, "setVisible", boolean.class, visible);
        }
    }

    /**
     * Samsung SystemUI hooks:
     * Unlocks Outdoor Mode switch in Quick Settings Brightness Detail dialog.
     */
    private void hookSystemUI(ClassLoader cl) {
        Log.i(TAG, "Starting hookSystemUI...");
        try {
            Class<?> adapterCls = cl.loadClass("com.android.systemui.settings.brightness.BrightnessDetailAdapter");
            hookAllMethods(adapterCls, "createDetailView", chain -> {
                View detailView = (View) chain.proceed();
                if (detailView instanceof ViewGroup && isPref("pref_display_outdoor_mode", true)) {
                    try {
                        Context ctx = (Context) chain.getArg(0);
                        ViewGroup root = (ViewGroup) detailView;

                        if (root.findViewWithTag("s23_outdoor_mode_switch") == null) {
                            // Add divider view
                            View divider = new View(ctx);
                            divider.setTag("s23_outdoor_mode_divider");
                            int divHeight = (int) (1 * ctx.getResources().getDisplayMetrics().density);
                            if (divHeight < 1) divHeight = 1;
                            LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, divHeight
                            );
                            int margin8 = (int) (8 * ctx.getResources().getDisplayMetrics().density);
                            divLp.setMargins(0, margin8, 0, margin8);
                            divider.setLayoutParams(divLp);
                            divider.setBackgroundColor(0x1fffffff);
                            root.addView(divider);

                            // Inflate switch preference using SecQSSwitchPreference.inflateSwitch(ctx, root)
                            Class<?> prefCls = cl.loadClass("com.android.systemui.qs.SecQSSwitchPreference");
                            Method inflateMethod = prefCls.getMethod("inflateSwitch", Context.class, ViewGroup.class);
                            View switchPref = (View) inflateMethod.invoke(null, ctx, root);
                            switchPref.setTag("s23_outdoor_mode_switch");

                            // Find TextViews and switch inside
                            List<TextView> textViews = new ArrayList<>();
                            findViews(switchPref, TextView.class, textViews);
                            TextView titleView = !textViews.isEmpty() ? textViews.get(0) : null;
                            TextView summaryView = textViews.size() > 1 ? textViews.get(1) : null;

                            List<CompoundButton> switches = new ArrayList<>();
                            findViews(switchPref, CompoundButton.class, switches);
                            final CompoundButton switchWidget = !switches.isEmpty() ? switches.get(0) : null;

                            int titleRes = ctx.getResources().getIdentifier("sec_brightness_outdoor_mode_title", "string", ctx.getPackageName());
                            int summaryRes = ctx.getResources().getIdentifier("sec_brightness_outdoor_mode_summary", "string", ctx.getPackageName());
                            boolean isCs = isCzech(ctx);
                            String title = titleRes != 0 ? ctx.getString(titleRes) : (isCs ? "Venkovní režim" : "Outdoor mode");
                            String summary = summaryRes != 0 ? ctx.getString(summaryRes) : (isCs ? "Zvýšit jas obrazovky pro venkovní použití. Režim se po 15 minutách automaticky vypne." : "Increase screen brightness for outdoor viewing. After 15 minutes, Outdoor mode will turn off.");

                            if (titleView != null) {
                                titleView.setText(title);
                            }
                            if (summaryView != null) {
                                summaryView.setText(summary);
                                summaryView.setVisibility(View.VISIBLE);
                            }

                            if (switchWidget != null) {
                                int curVal = Settings.System.getInt(ctx.getContentResolver(), "display_outdoor_mode", 0);
                                switchWidget.setChecked(curVal == 1);

                                switchWidget.setOnCheckedChangeListener((btn, isChecked) -> {
                                    Settings.System.putInt(ctx.getContentResolver(), "display_outdoor_mode", isChecked ? 1 : 0);
                                });

                                switchPref.setOnClickListener(v -> switchWidget.toggle());

                                // Keep switch state synced with Settings.System
                                final android.database.ContentObserver observer = new android.database.ContentObserver(new android.os.Handler(android.os.Looper.getMainLooper())) {
                                    @Override
                                    public void onChange(boolean selfChange) {
                                        try {
                                            int updated = Settings.System.getInt(ctx.getContentResolver(), "display_outdoor_mode", 0);
                                            if (switchWidget.isChecked() != (updated == 1)) {
                                                switchWidget.setChecked(updated == 1);
                                            }
                                        } catch (Throwable ignored) {}
                                    }
                                };
                                try {
                                    ctx.getContentResolver().registerContentObserver(
                                        Settings.System.getUriFor("display_outdoor_mode"),
                                        false,
                                        observer
                                    );
                                    switchPref.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                                        @Override
                                        public void onViewAttachedToWindow(View v) {}

                                        @Override
                                        public void onViewDetachedFromWindow(View v) {
                                            try {
                                                ctx.getContentResolver().unregisterContentObserver(observer);
                                            } catch (Throwable ignored) {}
                                        }
                                    });
                                } catch (Throwable t) {
                                    Log.w(TAG, "Failed to register ContentObserver for outdoor mode", t);
                                }
                            }
                            Log.i(TAG, "Successfully injected Outdoor Mode switch into BrightnessDetailView");
                        }
                    } catch (Throwable t) {
                        Log.w(TAG, "Error injecting outdoor mode into BrightnessDetailView", t);
                    }
                }
                return detailView;
            });
            Log.i(TAG, "Hooked BrightnessDetailAdapter in SystemUI");
        } catch (Throwable t) {
            Log.w(TAG, "Failed to hook BrightnessDetailAdapter: " + t.getMessage());
        }

        SystemUiHooks.install(this, cl);
        ChargingInfoHooks.installInSystemUI(this, cl);
        hookLockscreenWidgets(cl, "com.android.systemui");
    }

    /**
     * Expands the One UI lock screen ComplicationWidget grid past Samsung's 1-row phone limit
     * by intercepting {@code ComplicationWidgetSizeManager#getRow()} across direct and plugin
     * ClassLoaders (SystemUI, AODService, and Wallpaper & Style DressRoom).
     */
    private void hookLockscreenWidgets(ClassLoader cl, String pkg) {
        try {
            Class<?> cls = cl.loadClass(COMPLICATION_SIZE_MANAGER_CLASS);
            hookComplicationSizeManager(cls, pkg + ":direct");
        } catch (Throwable ignored) {
        }

        if ("com.android.systemui".equals(pkg) || "com.samsung.android.app.dressroom".equals(pkg)) {
            try {
                hookConstructors(dalvik.system.PathClassLoader.class, chain -> {
                    Object res = chain.proceed();
                    Object thisObj = chain.getThisObject();
                    if (thisObj instanceof ClassLoader) {
                        String arg0 = (!chain.getArgs().isEmpty() && chain.getArg(0) instanceof String)
                                ? (String) chain.getArg(0) : "";
                        String desc = thisObj.toString();
                        if (arg0.contains("aodservice") || (desc != null && desc.contains("aodservice"))) {
                            try {
                                Class<?> cls = ((ClassLoader) thisObj).loadClass(COMPLICATION_SIZE_MANAGER_CLASS);
                                hookComplicationSizeManager(cls, pkg + ":pathcl");
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                    return res;
                });
            } catch (Throwable t) {
                Log.w(TAG, "Failed to hook PathClassLoader ctors in " + pkg + ": " + t.getMessage());
            }

            try {
                Method loadClassMethod = ClassLoader.class.getDeclaredMethod("loadClass", String.class, boolean.class);
                hook(loadClassMethod).intercept(chain -> {
                    if (mComplicationHooked) return chain.proceed();
                    Object res = chain.proceed();
                    if (!chain.getArgs().isEmpty()
                            && COMPLICATION_SIZE_MANAGER_CLASS.equals(chain.getArg(0))
                            && res instanceof Class<?>) {
                        hookComplicationSizeManager((Class<?>) res, pkg + ":loadClass");
                    }
                    return res;
                });
            } catch (Throwable t) {
                Log.w(TAG, "Failed to hook ClassLoader.loadClass in " + pkg + ": " + t.getMessage());
            }
        }
    }

    private void hookComplicationSizeManager(Class<?> cls, String source) {
        if (cls == null) return;
        synchronized (mHookedComplicationManagers) {
            if (!mHookedComplicationManagers.add(cls)) return;
        }
        mComplicationHooked = true;
        hookAllMethods(cls, "getRow", chain -> {
            Object orig = chain.proceed();
            if (orig instanceof Integer && ((Integer) orig) == 1) {
                int want = getIntPref(PREF_LOCKSCREEN_WIDGET_ROWS, 0);
                if (want >= 2 && want <= 4) {
                    if (!mLoggedLockscreenWidgetOverride) {
                        mLoggedLockscreenWidgetOverride = true;
                        Log.i(TAG, "Lockscreen widget rows override (" + source + "): 1 -> " + want);
                    }
                    return want;
                }
            }
            return orig;
        });
        hookAllMethods(cls, "isBiggerSize", chain -> {
            Object orig = chain.proceed();
            if (orig instanceof Boolean && !((Boolean) orig)) {
                int want = getIntPref(PREF_LOCKSCREEN_WIDGET_ROWS, 0);
                if (want >= 2 && want <= 4) {
                    return true;
                }
            }
            return orig;
        });
        Log.i(TAG, "Hooked ComplicationWidgetSizeManager#getRow+isBiggerSize (" + source + ") in PID " + Process.myPid());
    }

    private <T extends View> void findViews(View root, Class<T> clazz, List<T> results) {
        if (clazz.isInstance(root)) {
            results.add(clazz.cast(root));
        }
        if (root instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) root;
            for (int i = 0; i < vg.getChildCount(); i++) {
                findViews(vg.getChildAt(i), clazz, results);
            }
        }
    }
}
