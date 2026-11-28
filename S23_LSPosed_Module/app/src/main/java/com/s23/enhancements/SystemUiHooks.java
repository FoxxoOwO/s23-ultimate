package com.s23.enhancements;

import android.content.Context;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.TrafficStats;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import io.github.libxposed.api.XposedInterface;

/**
 * SystemUI hooks modeled after proven One UI 8.5 / 9 techniques:
 * - ExpandableNotificationRowController#setSystemExpanded + setChildrenExpanded for notifications
 * - ART deoptimize on NotificationIconContainer callers + Resources.getInteger for notif icon cap
 * - TextView attach + QSClockIndicatorView.notifyTimeChanged to attach onto the live status bar root
 * - ViewTreeObserver.OnPreDrawListener for live edge/item spacing, Wi-Fi/mobile icon merge, back button & pixel shift
 * - ViewGroup#dispatchTouchEvent (non-consuming) filtered to statusRoot + ComposeView battery discovery
 */
final class SystemUiHooks {

    private static final String TAG = "S23Enhancer";
    private static final String SYSUI_PKG = "com.android.systemui";
    private static final String GLOBAL_PREFIX = "s23enh_";
    private static final String CMD_RESTART_SYSUI = "s23enh_cmd_restart_systemui";

    // Notification prefs
    private static final String PREF_NOTIF_AUTO_EXPAND = "pref_notif_auto_expand";
    private static final String PREF_NOTIF_GROUPS_ONE_TAP = "pref_notif_expand_groups_one_tap";
    private static final String PREF_NOTIF_KEEP_SILENT_SMALL = "pref_notif_keep_silent_small";

    // Status bar layout & icon prefs
    private static final String PREF_SB_EDGE_MODE = "pref_sb_edge_space_mode";
    private static final String PREF_SB_EDGE_DP = "pref_sb_edge_space_dp";
    private static final String PREF_SB_ITEM_SPACING = "pref_sb_item_spacing";
    private static final String PREF_SB_MAX_NOTIF_ICONS = "pref_sb_max_notif_icons";
    private static final String PREF_SB_ONE_CONN_ICON = "pref_sb_one_connection_icon";
    private static final String PREF_SB_BACK_BUTTON = "pref_sb_back_button";
    private static final String PREF_SB_HIDE_BATTERY_PCT = "pref_sb_hide_battery_pct";
    private static final String PREF_SB_PILL_WIDTH = "pref_sb_battery_pill_width";
    private static final String PREF_SB_BURN_IN = "pref_sb_burn_in_protect";
    private static final String PREF_SB_NET_SPEED = "pref_sb_net_speed";
    private static final String PREF_SB_NET_SPEED_MODE = "pref_sb_net_speed_mode";
    private static final String PREF_SB_NET_SPEED_AUTO_HIDE = "pref_sb_net_speed_auto_hide";

    // Taps & feedback prefs
    private static final String PREF_SB_TAP_VIBRATE = "pref_sb_tap_vibrate";
    private static final String PREF_SB_TAP_VIBRATE_STRENGTH = "pref_sb_tap_vibrate_strength";
    private static final String PREF_SB_DOUBLE_TAP_SLEEP = "pref_sb_double_tap_sleep";
    private static final String PREF_SB_TAP_PREFIX = "pref_sb_tap_";
    private static final String PREF_SB_LONG_PREFIX = "pref_sb_long_";
    private static final String[] ACTION_TARGETS = {"battery", "clock", "camera_hole", "wifi", "mobile"};

    private static final String TAG_BACK_BTN = "s23_sb_back_btn";
    private static final String TAG_NET_SPEED = "s23_sb_net_speed";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static volatile boolean sInstalled;
    private static boolean sInitDone;
    private static Context sAppCtx;
    private static HookEntry sModule;

    // Cached preferences refreshed via Settings.Global ContentObserver (avoids per-frame IPC)
    private static volatile boolean sCacheLoaded;
    private static volatile int sPrefEdgeMode = 0;
    private static volatile int sPrefEdgeDp = 4;
    private static volatile int sPrefItemSpacing = -1;
    private static volatile int sPrefMaxNotifIcons = 0;
    private static volatile boolean sPrefOneConn = false;
    private static volatile boolean sPrefBackBtn = false;
    private static volatile boolean sPrefBurnIn = true;
    private static volatile boolean sPrefNetSpeed = false;
    private static volatile int sPrefNetSpeedMode = 0;
    private static volatile boolean sPrefNetSpeedAutoHide = true;
    private static volatile boolean sPrefTapVibrate = true;
    private static volatile int sPrefTapVibrateStrength = 1;
    private static volatile boolean sPrefDoubleTapSleep = false;
    private static volatile boolean sPrefHideBatteryPct = false;
    private static volatile int sPrefPillWidth = -1;
    private static Field fPillText;
    private static Field fPillWidth;
    private static Field fPillFont;
    private static Paint sPillPaint;
    private static final ConcurrentHashMap<String, String> sPrefActions = new ConcurrentHashMap<>();

    // Cached status bar child views
    private static WeakReference<View> sSystemIconsRef = new WeakReference<>(null);
    private static WeakReference<View> sRightClockRef = new WeakReference<>(null);
    private static WeakReference<View> sWifiRef = new WeakReference<>(null);
    private static WeakReference<View> sNotifIconsRef = new WeakReference<>(null);

    // Resource IDs resolved once from com.android.systemui
    private static int sClockId;
    private static int sBatteryId;
    private static int sBatteryComposableId;
    private static int sSystemIconsId;
    private static int sStatusContentsId;
    private static int sRightClockContainerId;
    private static int sWifiSignalId;
    private static int sMobileSignalId;
    private static int sStatusIconsId;
    private static int sNotifIconsId;
    private static int sIdMaxNotifStatic = -2;

    // Live status bar views
    private static volatile View sStatusRoot;
    private static WeakReference<TextView> sClockRef = new WeakReference<>(null);
    private static final List<WeakReference<TextView>> sClockCandidates = new ArrayList<>();
    private static long sLastClockCheck;
    private static TextView sBackBtn;
    private static TextView sNetSpeedView;
    private static int sNetSpeedLastMode = -1;
    private static Runnable sNetSpeedTick;
    private static boolean sNetSpeedRunning;
    private static long sLastNetRx = -1L;
    private static long sLastNetTx = -1L;
    private static long sLastNetTimeMs;
    private static ViewTreeObserver sObserver;
    private static ViewTreeObserver.OnPreDrawListener sPreDraw;

    // Edge & item spacing originals
    private static WeakReference<View> sPadView = new WeakReference<>(null);
    private static int[] sOrigPad;
    private static final WeakHashMap<View, int[]> sOrigItemGap = new WeakHashMap<>();

    // One connection icon merge state
    private static WeakReference<View> sMobileBox = new WeakReference<>(null);
    private static boolean sMergeApplied;
    private static volatile boolean sMergeHide;
    private static Class<?> sMobileCls;
    private static boolean sMergeHooked;
    private static boolean sMergeHookFailed;

    // Burn-in pixel-shift state
    private static final int[][] SHIFT = {
            {0, 0}, {2, 0}, {3, 1}, {1, 1}, {-1, 1}, {-3, 0},
            {-2, -1}, {0, -1}, {2, -1}, {1, 0}, {-1, 0}, {-2, 1}
    };
    private static int sShiftStep;
    private static WeakReference<View> sShiftedRef = new WeakReference<>(null);
    private static int[] sCurShift = {0, 0};
    private static float sLastShiftX;
    private static float sLastShiftY;
    private static Runnable sShiftTick;

    // Touch state
    private static float sDownX;
    private static float sDownY;
    private static boolean sMoved;
    private static boolean sLongFired;
    private static boolean sTouchBack;
    private static boolean sTouchBg;
    private static String sDownTarget = "empty";
    private static Runnable sLongPressTask;
    private static long sLastBgTapMs;
    private static float sLastBgX;
    private static final int[] sLoc = new int[2];

    // Notification reflection cache
    private static Field fCtrlView, fSysExpanded, fUserChanged, fIsBundle, fIsSummary, fChildrenCont, fAttachedChildren, fEntryAdapter;
    private static Method mRowIsChildInGroup, mRowIsGroupExpanded, mRowIsExpanded, mRowNotifyHeight, mRowOnExpansionChanged;
    private static Method mAdapterImportance, mAdapterAmbient;
    private static Field fNicMaxIcons;

    private SystemUiHooks() {
    }

    static synchronized void install(HookEntry module, ClassLoader cl) {
        if (sInstalled) return;
        sInstalled = true;
        sModule = module;

        hookBatteryController(module, cl);
        hookBatteryPercent(module, cl);
        hookNotifications(module, cl);
        hookMaxNotificationIcons(module, cl);
        hookItemSpacing(module, cl);
        hookBurnInProtection(module, cl);
        hookClockAndStatusBar(module, cl);
        hookTouchRouter(module, cl);
    }

    // ------------------------------------------------------------------ 1. BatteryController

    private static void hookBatteryController(HookEntry module, ClassLoader cl) {
        try {
            Class<?> cls = cl.loadClass("com.android.systemui.statusbar.policy.BatteryControllerImpl");
            module.hookAllMethods(cls, "updatePowerSave", chain -> {
                Object res = chain.proceed();
                StatusBarActions.sBatteryController = chain.getThisObject();
                return res;
            });
            Log.i(TAG, "Hooked BatteryControllerImpl#updatePowerSave");
        } catch (Throwable t) {
            Log.w(TAG, "BatteryControllerImpl hook failed: " + t.getMessage());
        }
    }

    // ------------------------------------------------------------------ 2. Battery percentage hide

    private static void hookBatteryPercent(HookEntry module, ClassLoader cl) {
        // Hook SamsungBatteryMeterDrawable#shouldShowPercentageIcon → force false when pref enabled
        try {
            Class<?> cls = cl.loadClass("com.android.systemui.battery.SamsungBatteryMeterDrawable");
            module.hookAllMethods(cls, "shouldShowPercentageIcon", chain -> {
                if (sPrefHideBatteryPct) return false;
                return chain.proceed();
            });
            Log.i(TAG, "Hooked SamsungBatteryMeterDrawable#shouldShowPercentageIcon");
        } catch (Throwable t) {
            Log.w(TAG, "SamsungBatteryMeterDrawable pct hook failed: " + t.getMessage());
        }

        // Compose battery pill: blank the level text so the pill keeps its icon but loses the number
        try {
            Class<?> lambda = cl.loadClass("com.android.systemui.battery.view.SamsungBatteryComposeViewKt$$ExternalSyntheticLambda0");
            Method invoke = lambda.getDeclaredMethod("invoke", Object.class);
            module.hook(invoke).intercept(chain -> {
                if (sPrefHideBatteryPct) {
                    try {
                        if (fPillText == null) {
                            Field f = lambda.getDeclaredField("f$3");
                            f.setAccessible(true);
                            fPillText = f;
                        }
                        fPillText.set(chain.getThisObject(), "");
                    } catch (Throwable ignored) {}
                }
                return chain.proceed();
            });
            Log.i(TAG, "Hooked SamsungBatteryComposeView lambda (pct text blank)");
        } catch (Throwable t) {
            Log.w(TAG, "SamsungBatteryComposeView lambda hook failed: " + t.getMessage());
        }

        // Narrow the pill by the share (%) of the number's reserved width, 0 = Samsung default
        try {
            Class<?> stateCls = cl.loadClass("com.android.systemui.battery.BatteryLayoutState");
            module.hookConstructors(stateCls, chain -> {
                Object res = chain.proceed();
                if (sPrefHideBatteryPct && sPrefPillWidth > 0 && res != null) {
                    try {
                        if (fPillWidth == null) {
                            Field b = stateCls.getDeclaredField("totalWidthPx");
                            b.setAccessible(true);
                            Field c = stateCls.getDeclaredField("textFontSizePx");
                            c.setAccessible(true);
                            fPillFont = c;
                            fPillWidth = b;
                        }
                        Object self = chain.getThisObject();
                        float ts = fPillFont.getFloat(self);
                        int w = fPillWidth.getInt(self);
                        if (ts > 0 && w > 0) {
                            if (sPillPaint == null) {
                                sPillPaint = new Paint();
                                sPillPaint.setTypeface(Typeface.DEFAULT_BOLD);
                            }
                            sPillPaint.setTextSize(ts);
                            int reserve = Math.round(sPillPaint.measureText("100") * Math.min(100, sPrefPillWidth) / 100f);
                            fPillWidth.setInt(self, Math.max(0, w - reserve));
                        }
                    } catch (Throwable ignored) {}
                }
                return res;
            });
            Log.i(TAG, "Hooked BatteryLayoutState constructor (pill width)");
        } catch (Throwable t) {
            Log.w(TAG, "BatteryLayoutState hook failed: " + t.getMessage());
        }

        // Hook BatteryMeterView#updateShowPercent → also hide the external percent TextView
        try {
            Class<?> cls = cl.loadClass("com.android.systemui.battery.BatteryMeterView");
            module.hookAllMethods(cls, "updateShowPercent", chain -> {
                chain.proceed();
                if (sPrefHideBatteryPct) {
                    try {
                        Field f = cls.getDeclaredField("mBatteryPercentView");
                        f.setAccessible(true);
                        View v = (View) f.get(chain.getThisObject());
                        if (v != null) v.setVisibility(View.GONE);
                    } catch (Throwable ignored) {}
                }
                return null;
            });
            Log.i(TAG, "Hooked BatteryMeterView#updateShowPercent");
        } catch (Throwable t) {
            Log.w(TAG, "BatteryMeterView pct hook failed: " + t.getMessage());
        }
    }

    // ------------------------------------------------------------------ 3. Notifications

    private static void hookNotifications(HookEntry module, ClassLoader cl) {
        try {
            Class<?> rowCls = cl.loadClass("com.android.systemui.statusbar.notification.row.ExpandableNotificationRow");
            Class<?> ctrlCls = cl.loadClass("com.android.systemui.statusbar.notification.row.ExpandableNotificationRowController");
            Class<?> contCls = cl.loadClass("com.android.systemui.statusbar.notification.stack.NotificationChildrenContainer");

            fCtrlView = findField(ctrlCls, "mView");
            fSysExpanded = findField(rowCls, "mIsSystemExpanded");
            fUserChanged = findField(rowCls, "mHasUserChangedExpansion");
            fIsBundle = findField(rowCls, "mIsBundle");
            fIsSummary = findField(rowCls, "mIsSummaryWithChildren");
            fChildrenCont = findField(rowCls, "mChildrenContainer");
            fEntryAdapter = findField(rowCls, "mEntryAdapter");
            fAttachedChildren = findField(contCls, "mAttachedChildren");

            mRowIsChildInGroup = findMethod(rowCls, "isChildInGroup");
            mRowIsGroupExpanded = findMethod(rowCls, "isGroupExpanded");
            mRowIsExpanded = findMethod(rowCls, "isExpanded", boolean.class);
            mRowNotifyHeight = findMethod(rowCls, "notifyHeightChanged", String.class, boolean.class);
            mRowOnExpansionChanged = findMethod(rowCls, "onExpansionChanged", boolean.class, boolean.class);

            try {
                Class<?> eaCls = cl.loadClass("com.android.systemui.statusbar.notification.collection.EntryAdapter");
                mAdapterImportance = findMethod(eaCls, "getImportance");
                mAdapterAmbient = findMethod(eaCls, "isAmbient");
            } catch (Throwable t) {
                Log.w(TAG, "EntryAdapter silent check unavailable: " + t.getMessage());
            }

            Method setSys = ctrlCls.getDeclaredMethod("setSystemExpanded", boolean.class);
            module.hook(setSys).intercept(chain -> {
                boolean openAll = module.isPref(PREF_NOTIF_AUTO_EXPAND, false);
                boolean groupOpen = module.isPref(PREF_NOTIF_GROUPS_ONE_TAP, false);
                if (!openAll && !groupOpen) {
                    return chain.proceed();
                }
                try {
                    Object row = fCtrlView != null ? fCtrlView.get(chain.getThisObject()) : null;
                    if (row != null && !fIsBundle.getBoolean(row) && !fIsSummary.getBoolean(row)) {
                        boolean isChild = Boolean.TRUE.equals(mRowIsChildInGroup.invoke(row));
                        if (isChild) {
                            if (groupOpen && Boolean.TRUE.equals(mRowIsGroupExpanded.invoke(row))) {
                                if (!(module.isPref(PREF_NOTIF_KEEP_SILENT_SMALL, true) && isSilentRow(row))) {
                                    Object[] args = chain.getArgs().toArray();
                                    args[0] = Boolean.TRUE;
                                    return chain.proceed(args);
                                }
                            }
                        } else if (openAll) {
                            if (!(module.isPref(PREF_NOTIF_KEEP_SILENT_SMALL, true) && isSilentRow(row))) {
                                Object[] args = chain.getArgs().toArray();
                                args[0] = Boolean.TRUE;
                                return chain.proceed(args);
                            }
                        }
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "setSystemExpanded hook error: " + t.getMessage());
                }
                return chain.proceed();
            });
            Log.i(TAG, "Hooked ExpandableNotificationRowController#setSystemExpanded");

            Method setKids = rowCls.getDeclaredMethod("setChildrenExpanded", boolean.class);
            module.hook(setKids).intercept(chain -> {
                Object res = chain.proceed();
                if (!module.isPref(PREF_NOTIF_GROUPS_ONE_TAP, false)) return res;
                try {
                    Object summary = chain.getThisObject();
                    boolean open = !chain.getArgs().isEmpty() && Boolean.TRUE.equals(chain.getArg(0));
                    if (summary != null && fIsSummary.getBoolean(summary) && !fIsBundle.getBoolean(summary)) {
                        Object cont = fChildrenCont.get(summary);
                        if (cont != null) {
                            List<?> kids = (List<?>) fAttachedChildren.get(cont);
                            if (kids != null) {
                                boolean keepSilent = module.isPref(PREF_NOTIF_KEEP_SILENT_SMALL, true);
                                for (int i = 0; i < kids.size(); i++) {
                                    Object k = kids.get(i);
                                    if (k == null) continue;
                                    if (fUserChanged.getBoolean(k) || fSysExpanded.getBoolean(k) == open) continue;
                                    if (open && keepSilent && isSilentRow(k)) continue;
                                    boolean was = Boolean.TRUE.equals(mRowIsExpanded.invoke(k, false));
                                    fSysExpanded.setBoolean(k, open);
                                    mRowNotifyHeight.invoke(k, "S23Enhancer", false);
                                    mRowOnExpansionChanged.invoke(k, false, was);
                                }
                            }
                        }
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "setChildrenExpanded hook error: " + t.getMessage());
                }
                return res;
            });
            Log.i(TAG, "Hooked ExpandableNotificationRow#setChildrenExpanded");
        } catch (Throwable t) {
            Log.w(TAG, "Failed to hook notifications: " + t.getMessage());
        }
    }

    private static boolean isSilentRow(Object row) {
        if (row == null || fEntryAdapter == null) return false;
        try {
            Object ea = fEntryAdapter.get(row);
            if (ea == null) return false;
            if (mAdapterAmbient != null && Boolean.TRUE.equals(mAdapterAmbient.invoke(ea))) return true;
            if (mAdapterImportance != null) {
                Object imp = mAdapterImportance.invoke(ea);
                if (imp instanceof Integer && (Integer) imp < 3) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    // ------------------------------------------------------------------ 3. Notification Icon Cap

    private static void hookMaxNotificationIcons(HookEntry module, ClassLoader cl) {
        try {
            Method getInt = Resources.class.getDeclaredMethod("getInteger", int.class);
            module.hook(getInt).intercept(chain -> {
                Object res = chain.proceed();
                try {
                    int want = maxNotifIcons(module);
                    if (want <= 0) return res;
                    Resources r = (Resources) chain.getThisObject();
                    if (sIdMaxNotifStatic == -2) {
                        sIdMaxNotifStatic = r.getIdentifier("max_notif_static_icons", "integer", SYSUI_PKG);
                    }
                    int id = (Integer) chain.getArg(0);
                    if (id != 0 && id == sIdMaxNotifStatic) {
                        return want;
                    }
                } catch (Throwable ignored) {
                }
                return res;
            });
            Log.i(TAG, "Hooked Resources#getInteger for max_notif_static_icons");
        } catch (Throwable t) {
            Log.w(TAG, "Resources#getInteger hook failed: " + t.getMessage());
        }

        try {
            Class<?> nicCls = cl.loadClass("com.android.systemui.statusbar.phone.NotificationIconContainer");
            fNicMaxIcons = findField(nicCls, "mMaxIcons");
            for (Method m : nicCls.getDeclaredMethods()) {
                if ("calculateIconXTranslations".equals(m.getName())
                        || "applyIconStates".equals(m.getName())
                        || "onMeasure".equals(m.getName())) {
                    try {
                        module.deoptimize(m);
                        Log.i(TAG, "Deoptimized NotificationIconContainer#" + m.getName());
                    } catch (Throwable t) {
                        Log.w(TAG, "deoptimize failed for " + m.getName() + ": " + t.getMessage());
                    }
                }
            }
            for (String mName : new String[]{"onMeasure", "calculateIconXTranslations", "initResources"}) {
                module.hookAllMethods(nicCls, mName, chain -> {
                    applyMaxIconsToContainer(module, chain.getThisObject());
                    return chain.proceed();
                });
            }
            module.hookAllMethods(nicCls, "shouldForceOverflow", chain -> {
                int want = maxNotifIcons(module);
                if (want <= 0) return chain.proceed();
                Object[] args = chain.getArgs().toArray();
                if (args.length > 0 && args[args.length - 1] instanceof Integer) {
                    args[args.length - 1] = want;
                    return chain.proceed(args);
                }
                return chain.proceed();
            });
            Log.i(TAG, "Hooked NotificationIconContainer#shouldForceOverflow + mMaxIcons");
        } catch (Throwable t) {
            Log.w(TAG, "NotificationIconContainer hook failed: " + t.getMessage());
        }

        try {
            Class<?> vmCls = cl.loadClass(
                    "com.android.systemui.statusbar.notification.icon.ui.viewmodel.NotificationIconContainerStatusBarViewModel");
            module.hookConstructors(vmCls, chain -> {
                Object res = chain.proceed();
                if (maxNotifIcons(module) > 0) {
                    setFieldInt(chain.getThisObject(), "maxIcons", Integer.MAX_VALUE);
                }
                return res;
            });
            Log.i(TAG, "Hooked NotificationIconContainerStatusBarViewModel");
        } catch (Throwable t) {
            Log.w(TAG, "NotificationIconContainerStatusBarViewModel hook failed: " + t.getMessage());
        }
    }

    // ------------------------------------------------------------------ 4. Item Spacing & Burn-in

    private static void hookItemSpacing(HookEntry module, ClassLoader cl) {
        try {
            Class<?> sicCls = cl.loadClass("com.android.systemui.statusbar.phone.StatusIconContainer");
            for (Method m : sicCls.getDeclaredMethods()) {
                if (m.getName().startsWith("reloadDimens") || "onAttachedToWindow".equals(m.getName())) {
                    try {
                        module.hook(m).intercept(chain -> {
                            Object res = chain.proceed();
                            if (chain.getThisObject() instanceof View) {
                                int dp = itemSpacingDp(module);
                                if (dp >= 0) {
                                    float d = ((View) chain.getThisObject()).getResources().getDisplayMetrics().density;
                                    setFieldInt(chain.getThisObject(), "mIconSpacing", Math.round(dp * d));
                                }
                            }
                            return res;
                        });
                    } catch (Throwable ignored) {
                    }
                }
            }
            Log.i(TAG, "Hooked StatusIconContainer");
        } catch (Throwable t) {
            Log.w(TAG, "StatusIconContainer hook failed: " + t.getMessage());
        }
    }

    private static void hookBurnInProtection(HookEntry module, ClassLoader cl) {
        try {
            Class<?> cls = cl.loadClass("com.android.systemui.statusbar.phone.IndicatorMarqueeGardener");
            module.hookAllMethods(cls, "updateMarqueeValues", chain -> {
                if (!burnInEnabled(module)) return null;
                return chain.proceed();
            });
            Log.i(TAG, "Hooked IndicatorMarqueeGardener");
        } catch (Throwable t) {
            Log.w(TAG, "IndicatorMarqueeGardener hook failed: " + t.getMessage());
        }
    }

    // ------------------------------------------------------------------ 5. Clock, Root & Live Sync

    private static void hookClockAndStatusBar(HookEntry module, ClassLoader cl) {
        try {
            Method attach = TextView.class.getDeclaredMethod("onAttachedToWindow");
            module.hook(attach).intercept(chain -> {
                Object res = chain.proceed();
                if (chain.getThisObject() instanceof TextView) {
                    onTextViewAttached((TextView) chain.getThisObject());
                }
                return res;
            });
            Log.i(TAG, "Hooked TextView#onAttachedToWindow");
        } catch (Throwable t) {
            Log.w(TAG, "TextView#onAttachedToWindow hook failed: " + t.getMessage());
        }

        try {
            Class<?> clockCls = cl.loadClass("com.android.systemui.statusbar.policy.QSClockIndicatorView");
            Class<?> bellCls = cl.loadClass("com.android.systemui.statusbar.policy.QSClockBellSound");
            Method notify = clockCls.getDeclaredMethod("notifyTimeChanged", bellCls);
            module.hook(notify).intercept(chain -> {
                Object res = chain.proceed();
                if (chain.getThisObject() instanceof TextView) {
                    considerClock((TextView) chain.getThisObject());
                }
                return res;
            });
            Log.i(TAG, "Hooked QSClockIndicatorView#notifyTimeChanged");
        } catch (Throwable t) {
            Log.w(TAG, "QSClockIndicatorView#notifyTimeChanged hook failed: " + t.getMessage());
        }
    }

    private static void initOnce(Context anyCtx) {
        if (sInitDone || anyCtx == null) return;
        sInitDone = true;
        sAppCtx = anyCtx.getApplicationContext() != null ? anyCtx.getApplicationContext() : anyCtx;
        if (sModule != null) {
            sModule.setAppContext(sAppCtx);
        }
        Resources res = sAppCtx.getResources();
        sClockId = res.getIdentifier("clock", "id", SYSUI_PKG);
        sBatteryId = res.getIdentifier("battery", "id", SYSUI_PKG);
        sBatteryComposableId = res.getIdentifier("battery_meter_composable_view", "id", SYSUI_PKG);
        sSystemIconsId = res.getIdentifier("system_icons", "id", SYSUI_PKG);
        sStatusContentsId = res.getIdentifier("status_bar_contents", "id", SYSUI_PKG);
        sRightClockContainerId = res.getIdentifier("right_clock_container", "id", SYSUI_PKG);
        sWifiSignalId = res.getIdentifier("wifi_signal", "id", SYSUI_PKG);
        sMobileSignalId = res.getIdentifier("mobile_signal", "id", SYSUI_PKG);
        sStatusIconsId = res.getIdentifier("statusIcons", "id", SYSUI_PKG);
        sNotifIconsId = res.getIdentifier("notificationIcons", "id", SYSUI_PKG);

        refreshPrefCache(sModule);
        registerRestartTriggers(sAppCtx);
        startShiftLoop();
        Log.i(TAG, "SystemUiHooks initialized: clockId=" + sClockId
                + " systemIconsId=" + sSystemIconsId + " contentsId=" + sStatusContentsId
                + " notifIconsId=" + sNotifIconsId + " batCompId=" + sBatteryComposableId
                + " maxNotif=" + sPrefMaxNotifIcons
                + " tapBattery=" + actionPref(PREF_SB_TAP_PREFIX, "battery"));
    }

    private static void ensureCache(HookEntry mod) {
        if (!sCacheLoaded && mod != null) {
            refreshPrefCache(mod);
        }
    }

    private static void refreshPrefCache(HookEntry mod) {
        if (mod == null) return;
        sPrefEdgeMode = mod.getIntPref(PREF_SB_EDGE_MODE, 0);
        sPrefEdgeDp = mod.getIntPref(PREF_SB_EDGE_DP, 4);
        sPrefItemSpacing = mod.getIntPref(PREF_SB_ITEM_SPACING, -1);
        sPrefMaxNotifIcons = mod.getIntPref(PREF_SB_MAX_NOTIF_ICONS, 0);
        sPrefOneConn = mod.isPref(PREF_SB_ONE_CONN_ICON, false);
        sPrefBackBtn = mod.isPref(PREF_SB_BACK_BUTTON, false);
        sPrefBurnIn = mod.isPref(PREF_SB_BURN_IN, true);
        sPrefNetSpeed = mod.isPref(PREF_SB_NET_SPEED, false);
        sPrefNetSpeedMode = mod.getIntPref(PREF_SB_NET_SPEED_MODE, 0);
        sPrefNetSpeedAutoHide = mod.isPref(PREF_SB_NET_SPEED_AUTO_HIDE, true);
        sPrefTapVibrate = mod.isPref(PREF_SB_TAP_VIBRATE, true);
        sPrefTapVibrateStrength = mod.getIntPref(PREF_SB_TAP_VIBRATE_STRENGTH, 1);
        sPrefDoubleTapSleep = mod.isPref(PREF_SB_DOUBLE_TAP_SLEEP, false);
        sPrefHideBatteryPct = mod.isPref(PREF_SB_HIDE_BATTERY_PCT, false);
        sPrefPillWidth = mod.getIntPref(PREF_SB_PILL_WIDTH, -1);
        for (String target : ACTION_TARGETS) {
            String tapKey = PREF_SB_TAP_PREFIX + target;
            String longKey = PREF_SB_LONG_PREFIX + target;
            sPrefActions.put(tapKey, mod.getStringPref(tapKey, StatusBarActions.ACTION_NONE));
            sPrefActions.put(longKey, mod.getStringPref(longKey, StatusBarActions.ACTION_NONE));
        }
        sCacheLoaded = true;
    }

    private static int maxNotifIcons(HookEntry mod) {
        ensureCache(mod);
        return sPrefMaxNotifIcons;
    }

    private static int itemSpacingDp(HookEntry mod) {
        ensureCache(mod);
        return sPrefItemSpacing;
    }

    private static boolean burnInEnabled(HookEntry mod) {
        ensureCache(mod);
        return sPrefBurnIn;
    }

    private static String actionPref(String prefix, String target) {
        String val = sPrefActions.get(prefix + target);
        return val != null ? val : StatusBarActions.ACTION_NONE;
    }

    private static void onTextViewAttached(TextView tv) {
        if (tv == null) return;
        if (!sInitDone) initOnce(tv.getContext());
        if (sClockId == 0 || tv.getId() != sClockId) return;
        if (TAG_BACK_BTN.equals(tv.getTag()) || TAG_NET_SPEED.equals(tv.getTag())) return;
        if (!tv.getClass().getName().contains("Clock")) return;
        if (!(tv.getParent() instanceof LinearLayout)) return;
        for (int i = sClockCandidates.size() - 1; i >= 0; i--) {
            if (sClockCandidates.get(i).get() == null) sClockCandidates.remove(i);
        }
        sClockCandidates.add(new WeakReference<>(tv));
        TextView cur = sClockRef.get();
        if (cur == null || !cur.isAttachedToWindow() || !cur.isShown()) {
            attachToClock(tv);
        }
    }

    private static void considerClock(TextView tv) {
        if (tv == null) return;
        if (!sInitDone) initOnce(tv.getContext());
        if (sClockId == 0 || tv.getId() != sClockId) return;
        if (TAG_BACK_BTN.equals(tv.getTag()) || TAG_NET_SPEED.equals(tv.getTag())) return;
        if (!tv.isShown() || !(tv.getParent() instanceof LinearLayout)) return;

        TextView cur = sClockRef.get();
        if (cur == tv && sStatusRoot == tv.getRootView() && sObserver != null && sObserver.isAlive()) {
            return;
        }
        if (cur != null && cur.isShown() && sStatusRoot == cur.getRootView()) {
            return;
        }
        sClockCandidates.add(new WeakReference<>(tv));
        attachToClock(tv);
    }

    private static void attachToClock(TextView clock) {
        detachClockObserver();
        sClockRef = new WeakReference<>(clock);
        sStatusRoot = clock.getRootView();

        sPreDraw = () -> {
            syncStatusBar();
            return true;
        };
        sObserver = clock.getViewTreeObserver();
        if (sObserver != null && sObserver.isAlive()) {
            sObserver.addOnPreDrawListener(sPreDraw);
        }
        syncStatusBar();
        View bat = findBattery(sStatusRoot);
        Log.i(TAG, "Attached to status bar clock: " + clock.getClass().getName()
                + " root=" + (sStatusRoot != null ? sStatusRoot.getClass().getSimpleName() : "null")
                + " battery=" + (bat != null ? bat.getClass().getSimpleName() : "null"));
    }

    private static void detachClockObserver() {
        try {
            if (sObserver != null && sObserver.isAlive() && sPreDraw != null) {
                sObserver.removeOnPreDrawListener(sPreDraw);
            }
        } catch (Throwable ignored) {
        }
        sObserver = null;
        sPreDraw = null;
    }

    private static void syncStatusBar() {
        View root = sStatusRoot;
        TextView clock = sClockRef.get();
        HookEntry mod = sModule;
        if (root == null || clock == null || mod == null) return;

        if (!clock.isShown()) {
            long now = SystemClock.uptimeMillis();
            if (now - sLastClockCheck >= 500L) {
                sLastClockCheck = now;
                for (int i = sClockCandidates.size() - 1; i >= 0; i--) {
                    TextView c = sClockCandidates.get(i).get();
                    if (c == null) {
                        sClockCandidates.remove(i);
                    } else if (c != clock && c.isShown() && c.getParent() instanceof LinearLayout) {
                        final TextView nextClock = c;
                        MAIN.post(() -> attachToClock(nextClock));
                        return;
                    }
                }
            }
        }

        applyEdgeSpacing(root);
        applyClockRowGap(root);
        applyOneConnectionIcon(mod, root);
        syncNotifIconsMax(root);
        syncBackButton(clock);
        syncNetSpeed(root, clock);
    }

    private static void applyMaxIconsToContainer(HookEntry module, Object container) {
        if (!(container instanceof View) || fNicMaxIcons == null) return;
        View v = (View) container;
        int nicId = sNotifIconsId;
        if (nicId == 0) {
            nicId = v.getResources().getIdentifier("notificationIcons", "id", SYSUI_PKG);
            if (nicId != 0) sNotifIconsId = nicId;
        }
        if (nicId == 0 || v.getId() != nicId) return;
        int want = maxNotifIcons(module);
        if (want <= 0) return;
        try {
            if (fNicMaxIcons.getInt(v) != want) {
                fNicMaxIcons.setInt(v, want);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void syncNotifIconsMax(View root) {
        if (sNotifIconsId == 0 || fNicMaxIcons == null) return;
        int want = sPrefMaxNotifIcons;
        if (want <= 0) return;
        View nic = sNotifIconsRef.get();
        if (nic == null || !nic.isAttachedToWindow() || nic.getRootView() != root) {
            nic = root.findViewById(sNotifIconsId);
            sNotifIconsRef = new WeakReference<>(nic);
        }
        if (nic == null) return;
        try {
            if (fNicMaxIcons.getInt(nic) != want) {
                fNicMaxIcons.setInt(nic, want);
                nic.requestLayout();
            }
        } catch (Throwable ignored) {
        }
    }

    private static void applyEdgeSpacing(View root) {
        if (sStatusContentsId == 0) return;
        View v = sPadView.get();
        if (v == null || !v.isAttachedToWindow() || v.getRootView() != root) {
            v = root.findViewById(sStatusContentsId);
            sPadView = new WeakReference<>(v);
            sOrigPad = null;
        }
        if (v == null) return;

        int mode = sPrefEdgeMode;
        if (mode != 0) {
            int dp = (mode == 1) ? 4 : Math.max(0, Math.min(32, sPrefEdgeDp));
            if (sOrigPad == null) {
                sOrigPad = new int[]{v.getPaddingLeft(), v.getPaddingRight()};
            }
            int px = Math.round(dp * v.getResources().getDisplayMetrics().density);
            if (v.getPaddingLeft() != px || v.getPaddingRight() != px) {
                v.setPadding(px, v.getPaddingTop(), px, v.getPaddingBottom());
            }
        } else if (sOrigPad != null) {
            if (v.getPaddingLeft() != sOrigPad[0] || v.getPaddingRight() != sOrigPad[1]) {
                v.setPadding(sOrigPad[0], v.getPaddingTop(), sOrigPad[1], v.getPaddingBottom());
            }
            sOrigPad = null;
        }
    }

    private static void applyClockRowGap(View root) {
        if (sRightClockContainerId == 0) return;
        View v = sRightClockRef.get();
        if (v == null || !v.isAttachedToWindow() || v.getRootView() != root) {
            v = root.findViewById(sRightClockContainerId);
            sRightClockRef = new WeakReference<>(v);
        }
        if (v == null || !(v.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) return;
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
        int[] orig = sOrigItemGap.get(v);
        int gapDp = sPrefItemSpacing;
        if (gapDp < 0) {
            if (orig != null) {
                sOrigItemGap.remove(v);
                lp.setMarginStart(orig[0]);
                v.setLayoutParams(lp);
            }
            return;
        }
        if (orig == null) {
            orig = new int[]{lp.getMarginStart()};
            sOrigItemGap.put(v, orig);
        }
        int px = Math.round(gapDp * v.getResources().getDisplayMetrics().density);
        if (lp.getMarginStart() != px) {
            lp.setMarginStart(px);
            v.setLayoutParams(lp);
        }
    }

    private static void applyOneConnectionIcon(HookEntry mod, View root) {
        if (sMobileSignalId == 0 || sStatusIconsId == 0 || sWifiSignalId == 0) return;
        View box = sMobileBox.get();
        if (box == null || !box.isAttachedToWindow() || box.getRootView() != root) {
            View m = root.findViewById(sMobileSignalId);
            View row = root.findViewById(sStatusIconsId);
            box = null;
            if (m != null && row != null) {
                View cur = m;
                while (cur != null && cur.getParent() != row) {
                    ViewParent p = cur.getParent();
                    cur = p instanceof View ? (View) p : null;
                }
                box = cur;
            }
            sMobileBox = new WeakReference<>(box);
        }
        if (box == null) return;

        View wifi = sWifiRef.get();
        if (wifi == null || !wifi.isAttachedToWindow() || wifi.getRootView() != root) {
            wifi = root.findViewById(sWifiSignalId);
            sWifiRef = new WeakReference<>(wifi);
        }
        boolean wifiUp = wifi != null && wifi.isShown() && wifi.getWidth() > 0;
        boolean wantMerge = sPrefOneConn;

        if (wantMerge && wifiUp) {
            if (hookMobileBox(mod, box)) {
                if (!sMergeHide) {
                    sMergeHide = true;
                    relayoutBox(box);
                }
            }
            sMergeApplied = true;
            if (box.getAlpha() != 0f) box.setAlpha(0f);
        } else if (sMergeApplied || sMergeHide) {
            sMergeApplied = false;
            if (sMergeHide) {
                sMergeHide = false;
                relayoutBox(box);
            }
            if (box.getAlpha() != 1f) box.setAlpha(1f);
        }
    }

    private static void relayoutBox(View box) {
        box.requestLayout();
        if (box.getParent() instanceof View) {
            ((View) box.getParent()).requestLayout();
        }
    }

    private static boolean hookMobileBox(HookEntry mod, View box) {
        if (sMergeHooked) return true;
        if (sMergeHookFailed) return false;
        try {
            Method m = null;
            for (Class<?> k = box.getClass(); k != null && m == null; k = k.getSuperclass()) {
                try {
                    m = k.getDeclaredMethod("isIconVisible");
                } catch (NoSuchMethodException ignored) {
                }
            }
            if (m == null || m.getReturnType() != boolean.class || Modifier.isAbstract(m.getModifiers())) {
                sMergeHookFailed = true;
                return false;
            }
            sMobileCls = box.getClass();
            mod.hook(m).intercept(chain -> {
                if (sMergeHide && sMobileCls.isInstance(chain.getThisObject())) {
                    return false;
                }
                return chain.proceed();
            });
            sMergeHooked = true;
            Log.i(TAG, "Hooked mobile icon isIconVisible() on " + sMobileCls.getName());
            return true;
        } catch (Throwable t) {
            sMergeHookFailed = true;
            Log.w(TAG, "hookMobileBox failed: " + t.getMessage());
            return false;
        }
    }

    private static void syncBackButton(TextView clock) {
        boolean want = sPrefBackBtn;
        if (!(clock.getParent() instanceof LinearLayout)) return;
        LinearLayout parent = (LinearLayout) clock.getParent();

        if (sBackBtn != null && sBackBtn.getParent() != parent) {
            if (sBackBtn.getParent() instanceof ViewGroup) {
                ((ViewGroup) sBackBtn.getParent()).removeView(sBackBtn);
            }
            sBackBtn = null;
        }
        if (!want) {
            if (sBackBtn != null && sBackBtn.getVisibility() != View.GONE) {
                sBackBtn.setVisibility(View.GONE);
            }
            return;
        }
        if (sBackBtn == null) {
            Context ctx = clock.getContext();
            TextView btn = new TextView(ctx);
            btn.setTag(TAG_BACK_BTN);
            btn.setText("\u2039");
            btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
            btn.setTypeface(Typeface.DEFAULT_BOLD);
            btn.setGravity(Gravity.CENTER);
            float d = ctx.getResources().getDisplayMetrics().density;
            int pad = Math.round(6 * d);
            btn.setPadding(pad, 0, pad, 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
            lp.gravity = Gravity.CENTER_VERTICAL;
            lp.setMarginEnd(Math.round(4 * d));
            parent.addView(btn, 0, lp);
            sBackBtn = btn;
        }
        if (sBackBtn.getVisibility() != View.VISIBLE) {
            sBackBtn.setVisibility(View.VISIBLE);
        }
        int color = clock.getCurrentTextColor();
        if (sBackBtn.getCurrentTextColor() != color) {
            sBackBtn.setTextColor(color);
        }
    }

    // ------------------------------------------------------------------ Network Speed Meter

    private static void syncNetSpeed(View root, TextView clock) {
        boolean want = sPrefNetSpeed;
        LinearLayout parent = null;
        boolean inSystemIcons = false;
        View si = sSystemIconsRef.get();
        if (si == null || !si.isAttachedToWindow() || si.getRootView() != root) {
            si = sSystemIconsId != 0 ? root.findViewById(sSystemIconsId) : null;
            sSystemIconsRef = new WeakReference<>(si);
        }
        if (si instanceof LinearLayout) {
            parent = (LinearLayout) si;
            inSystemIcons = true;
        } else if (clock.getParent() instanceof LinearLayout) {
            parent = (LinearLayout) clock.getParent();
        }
        if (parent == null) return;

        if (sNetSpeedView != null && sNetSpeedView.getParent() != parent) {
            if (sNetSpeedView.getParent() instanceof ViewGroup) {
                ((ViewGroup) sNetSpeedView.getParent()).removeView(sNetSpeedView);
            }
            sNetSpeedView = null;
            sNetSpeedLastMode = -1;
        }

        if (!want) {
            stopNetSpeedLoop();
            if (sNetSpeedView != null && sNetSpeedView.getVisibility() != View.GONE) {
                sNetSpeedView.setVisibility(View.GONE);
            }
            return;
        }

        if (sNetSpeedView == null) {
            Context ctx = clock.getContext();
            TextView tv = new TextView(ctx);
            tv.setTag(TAG_NET_SPEED);
            tv.setIncludeFontPadding(false);
            tv.setFontFeatureSettings("tnum");
            tv.setTypeface(clock.getTypeface());
            tv.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            float d = ctx.getResources().getDisplayMetrics().density;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
            lp.gravity = Gravity.CENTER_VERTICAL;
            lp.setMarginStart(Math.round(4 * d));
            lp.setMarginEnd(Math.round(4 * d));
            int insertIdx = 0;
            if (!inSystemIcons) {
                int clkIdx = parent.indexOfChild(clock);
                insertIdx = clkIdx >= 0 ? clkIdx + 1 : parent.getChildCount();
            }
            parent.addView(tv, insertIdx, lp);
            sNetSpeedView = tv;
            sNetSpeedLastMode = -1;
        }

        int color = clock.getCurrentTextColor();
        if (sNetSpeedView.getCurrentTextColor() != color) {
            sNetSpeedView.setTextColor(color);
        }

        int mode = sPrefNetSpeedMode;
        if (mode != sNetSpeedLastMode) {
            if (mode == 1) {
                sNetSpeedView.setSingleLine(false);
                sNetSpeedView.setMaxLines(2);
                sNetSpeedView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 7.5f);
                sNetSpeedView.setLineSpacing(0f, 0.85f);
            } else {
                sNetSpeedView.setSingleLine(true);
                sNetSpeedView.setMaxLines(1);
                sNetSpeedView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
                sNetSpeedView.setLineSpacing(0f, 1.0f);
            }
            sNetSpeedLastMode = mode;
        }

        startNetSpeedLoop();
    }

    private static void startNetSpeedLoop() {
        if (sNetSpeedRunning) return;
        sNetSpeedRunning = true;
        if (sNetSpeedTick == null) {
            sNetSpeedTick = new Runnable() {
                @Override
                public void run() {
                    if (!sNetSpeedRunning) return;
                    long delay = updateNetSpeedOnce();
                    if (sNetSpeedRunning) {
                        MAIN.postDelayed(this, delay);
                    }
                }
            };
        }
        MAIN.removeCallbacks(sNetSpeedTick);
        MAIN.post(sNetSpeedTick);
    }

    private static void stopNetSpeedLoop() {
        sNetSpeedRunning = false;
        if (sNetSpeedTick != null) {
            MAIN.removeCallbacks(sNetSpeedTick);
        }
        sLastNetRx = -1L;
        sLastNetTx = -1L;
        sLastNetTimeMs = 0L;
    }

    private static long updateNetSpeedOnce() {
        TextView tv = sNetSpeedView;
        if (!sPrefNetSpeed) {
            stopNetSpeedLoop();
            if (tv != null && tv.getVisibility() != View.GONE) {
                tv.setVisibility(View.GONE);
            }
            return 1000L;
        }
        if (!isInteractive()) {
            sLastNetRx = -1L;
            sLastNetTx = -1L;
            sLastNetTimeMs = 0L;
            return 3000L;
        }
        if (tv == null || !tv.isAttachedToWindow()) {
            return 1000L;
        }

        long rx = TrafficStats.getTotalRxBytes();
        long tx = TrafficStats.getTotalTxBytes();
        long now = SystemClock.elapsedRealtime();

        if (rx < 0 || tx < 0 || sLastNetRx < 0 || sLastNetTx < 0 || now <= sLastNetTimeMs) {
            sLastNetRx = rx;
            sLastNetTx = tx;
            sLastNetTimeMs = now;
            if (sPrefNetSpeedAutoHide) {
                if (tv.getVisibility() != View.GONE) tv.setVisibility(View.GONE);
            } else {
                int mode = sPrefNetSpeedMode;
                tv.setText(mode == 1 ? "\u21930B\n\u21910B" : "0 B/s");
                if (tv.getVisibility() != View.VISIBLE) tv.setVisibility(View.VISIBLE);
            }
            return 1000L;
        }

        long dt = now - sLastNetTimeMs;
        long rxBps = Math.max(0L, (rx - sLastNetRx) * 1000L / dt);
        long txBps = Math.max(0L, (tx - sLastNetTx) * 1000L / dt);
        sLastNetRx = rx;
        sLastNetTx = tx;
        sLastNetTimeMs = now;

        int mode = sPrefNetSpeedMode;
        boolean autoHide = sPrefNetSpeedAutoHide;
        long activeBps = (mode == 2) ? rxBps : (rxBps + txBps);

        if (autoHide && activeBps < 1024L) {
            if (tv.getVisibility() != View.GONE) tv.setVisibility(View.GONE);
        } else {
            String text;
            if (mode == 1) {
                text = "\u2193" + formatSpeed(rxBps, true) + "\n\u2191" + formatSpeed(txBps, true);
            } else if (mode == 2) {
                text = formatSpeed(rxBps, false);
            } else {
                text = formatSpeed(rxBps + txBps, false);
            }
            if (!text.contentEquals(tv.getText())) {
                tv.setText(text);
            }
            if (tv.getVisibility() != View.VISIBLE) {
                tv.setVisibility(View.VISIBLE);
            }
        }
        return 1000L;
    }

    static String formatSpeed(long bps, boolean compact) {
        if (bps < 1024L) {
            return compact ? (bps + "B") : (bps + " B/s");
        }
        double kb = bps / 1024.0;
        if (kb < 1000.0) {
            String num = kb < 100.0
                    ? String.format(Locale.US, "%.1f", kb)
                    : String.format(Locale.US, "%.0f", kb);
            return compact ? (num + "K") : (num + " KB/s");
        }
        double mb = kb / 1024.0;
        String num = mb < 100.0
                ? String.format(Locale.US, "%.1f", mb)
                : String.format(Locale.US, "%.0f", mb);
        return compact ? (num + "M") : (num + " MB/s");
    }

    // ------------------------------------------------------------------ Burn-in Shift Loop

    private static void startShiftLoop() {
        if (sShiftTick != null) return;
        sShiftTick = new Runnable() {
            @Override
            public void run() {
                MAIN.removeCallbacks(this);
                boolean on = burnInEnabled(sModule) && isInteractive();
                applyPixelShift(on);
                MAIN.postDelayed(this, 120_000L);
            }
        };
        MAIN.postDelayed(sShiftTick, 15_000L);
    }

    private static boolean isInteractive() {
        try {
            PowerManager pm = sAppCtx != null ? sAppCtx.getSystemService(PowerManager.class) : null;
            return pm == null || pm.isInteractive();
        } catch (Throwable t) {
            return true;
        }
    }

    private static void applyPixelShift(boolean on) {
        View root = sStatusRoot;
        View row = (root != null && sStatusContentsId != 0) ? root.findViewById(sStatusContentsId) : null;
        View old = sShiftedRef.get();
        if (old != null && old != row) {
            if (old.getTranslationX() == sLastShiftX && old.getTranslationY() == sLastShiftY) {
                old.setTranslationX(sLastShiftX - sCurShift[0]);
                old.setTranslationY(sLastShiftY - sCurShift[1]);
            }
            sCurShift = new int[]{0, 0};
            sShiftedRef = new WeakReference<>(null);
        }
        if (row == null) return;
        if (row == old && (row.getTranslationX() != sLastShiftX || row.getTranslationY() != sLastShiftY)) {
            sCurShift = new int[]{0, 0};
        }
        float baseX = row.getTranslationX() - sCurShift[0];
        float baseY = row.getTranslationY() - sCurShift[1];
        if (on) {
            sShiftStep = (sShiftStep + 1) % SHIFT.length;
            sCurShift = SHIFT[sShiftStep].clone();
        } else {
            sCurShift = new int[]{0, 0};
        }
        row.setTranslationX(baseX + sCurShift[0]);
        row.setTranslationY(baseY + sCurShift[1]);
        sLastShiftX = row.getTranslationX();
        sLastShiftY = row.getTranslationY();
        sShiftedRef = new WeakReference<>(row);
    }

    // ------------------------------------------------------------------ 6. Touch Router

    private static void hookTouchRouter(HookEntry module, ClassLoader cl) {
        try {
            Class<?> sbWinCls = cl.loadClass("com.android.systemui.statusbar.window.StatusBarWindowView");
            Method dispatch = sbWinCls.getDeclaredMethod("dispatchTouchEvent", MotionEvent.class);
            module.hook(dispatch).intercept(chain -> {
                Object thiz = chain.getThisObject();
                if ((sStatusRoot == null || thiz == sStatusRoot) && !chain.getArgs().isEmpty()) {
                    Object arg = chain.getArg(0);
                    if (arg instanceof MotionEvent && thiz instanceof View) {
                        if (!sInitDone) {
                            initOnce(((View) thiz).getContext());
                        }
                        if (sStatusRoot == null) {
                            sStatusRoot = (View) thiz;
                        }
                        onStatusBarTouch(module, (View) thiz, (MotionEvent) arg);
                    }
                }
                return chain.proceed();
            });
            Log.i(TAG, "Hooked StatusBarWindowView#dispatchTouchEvent");
            return;
        } catch (Throwable t) {
            Log.w(TAG, "StatusBarWindowView#dispatchTouchEvent hook failed, falling back to ViewGroup: " + t.getMessage());
        }

        try {
            Method dispatch = ViewGroup.class.getDeclaredMethod("dispatchTouchEvent", MotionEvent.class);
            module.hook(dispatch).intercept(chain -> {
                if (chain.getThisObject() == sStatusRoot && !chain.getArgs().isEmpty()) {
                    Object arg = chain.getArg(0);
                    if (arg instanceof MotionEvent) {
                        onStatusBarTouch(module, (View) chain.getThisObject(), (MotionEvent) arg);
                    }
                }
                return chain.proceed();
            });
            Log.i(TAG, "Hooked ViewGroup#dispatchTouchEvent");
        } catch (Throwable t) {
            Log.w(TAG, "ViewGroup#dispatchTouchEvent hook failed: " + t.getMessage());
        }
    }

    private static void onStatusBarTouch(HookEntry mod, View root, MotionEvent e) {
        try {
            Context ctx = root.getContext();
            int slop = ViewConfiguration.get(ctx).getScaledTouchSlop();
            int action = e.getActionMasked();

            if (action == MotionEvent.ACTION_DOWN) {
                cancelLongPress();
                sDownX = e.getRawX();
                sDownY = e.getRawY();
                sMoved = false;
                sLongFired = false;
                sTouchBack = false;
                sTouchBg = false;
                sDownTarget = "empty";

                if (sBackBtn != null && hitView(sBackBtn, sDownX, sDownY)) {
                    sTouchBack = true;
                    return;
                }

                sDownTarget = hitTestTarget(root, sDownX, sDownY);
                if (!"empty".equals(sDownTarget)) {
                    String longAct = actionPref(PREF_SB_LONG_PREFIX, sDownTarget);
                    if (!StatusBarActions.ACTION_NONE.equals(longAct)) {
                        final String act = longAct;
                        final String tgt = sDownTarget;
                        sLongPressTask = () -> {
                            sLongPressTask = null;
                            if (!sMoved) {
                                sLongFired = true;
                                Log.i(TAG, "StatusBar long-press target=" + tgt + " action=" + act);
                                haptic(root, ctx, true);
                                StatusBarActions.execute(ctx, act);
                            }
                        };
                        MAIN.postDelayed(sLongPressTask, ViewConfiguration.getLongPressTimeout());
                    }
                } else {
                    sTouchBg = sPrefDoubleTapSleep;
                }
            } else if (action == MotionEvent.ACTION_MOVE) {
                if (!sMoved && (Math.abs(e.getRawX() - sDownX) > slop || Math.abs(e.getRawY() - sDownY) > slop)) {
                    sMoved = true;
                    sTouchBg = false;
                    sTouchBack = false;
                    cancelLongPress();
                }
            } else if (action == MotionEvent.ACTION_CANCEL) {
                sTouchBg = false;
                sTouchBack = false;
                cancelLongPress();
            } else if (action == MotionEvent.ACTION_UP) {
                cancelLongPress();
                if (!sMoved && !sLongFired) {
                    if (sTouchBack) {
                        Log.i(TAG, "StatusBar gesture target=back action=back");
                        haptic(root, ctx, false);
                        StatusBarActions.execute(ctx, StatusBarActions.ACTION_BACK);
                    } else if (!"empty".equals(sDownTarget)) {
                        String tapAct = actionPref(PREF_SB_TAP_PREFIX, sDownTarget);
                        if (!StatusBarActions.ACTION_NONE.equals(tapAct)) {
                            Log.i(TAG, "StatusBar gesture target=" + sDownTarget + " action=" + tapAct);
                            haptic(root, ctx, false);
                            StatusBarActions.execute(ctx, tapAct);
                        }
                    } else if (sTouchBg && (e.getEventTime() - e.getDownTime() < 350L)) {
                        long now = e.getEventTime();
                        float dtSlop = slop * 3f;
                        if (now - sLastBgTapMs < ViewConfiguration.getDoubleTapTimeout()
                                && Math.abs(e.getRawX() - sLastBgX) < dtSlop * 3f) {
                            sLastBgTapMs = 0L;
                            Log.i(TAG, "StatusBar gesture target=empty action=sleep");
                            haptic(root, ctx, false);
                            StatusBarActions.execute(ctx, StatusBarActions.ACTION_SLEEP);
                        } else {
                            sLastBgTapMs = now;
                            sLastBgX = e.getRawX();
                        }
                    }
                }
                sTouchBg = false;
                sTouchBack = false;
            }
        } catch (Throwable t) {
            Log.w(TAG, "onStatusBarTouch error: " + t.getMessage());
        }
    }

    private static void haptic(View v, Context ctx, boolean isLong) {
        StatusBarActions.performHaptic(
                v,
                ctx,
                sPrefTapVibrate,
                sPrefTapVibrateStrength,
                isLong);
    }

    private static void cancelLongPress() {
        if (sLongPressTask != null) {
            MAIN.removeCallbacks(sLongPressTask);
            sLongPressTask = null;
        }
    }

    private static String hitTestTarget(View root, float x, float y) {
        View bat = findBattery(root);
        if (bat != null && hitView(bat, x, y)) return "battery";
        TextView clock = sClockRef.get();
        if (clock != null && hitView(clock, x, y)) return "clock";
        if (sWifiSignalId != 0) {
            View wifi = root.findViewById(sWifiSignalId);
            if (wifi != null && hitView(wifi, x, y)) return "wifi";
        }
        if (sMobileSignalId != 0 && !sMergeHide) {
            View mobile = root.findViewById(sMobileSignalId);
            if (mobile != null && hitView(mobile, x, y)) return "mobile";
        }
        if (hitCutout(root, x, y)) return "camera_hole";
        return "empty";
    }

    private static View findBattery(View root) {
        if (root == null) return null;
        if (sBatteryComposableId != 0) {
            View b = root.findViewById(sBatteryComposableId);
            if (b != null && b.isShown() && b.getWidth() > 0) return b;
        }
        if (sBatteryId != 0) {
            View b = root.findViewById(sBatteryId);
            if (b != null && b.isShown() && b.getWidth() > 0) return b;
        }
        if (sSystemIconsId != 0) {
            View si = root.findViewById(sSystemIconsId);
            if (si instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) si;
                for (int i = g.getChildCount() - 1; i >= 0; i--) {
                    View c = g.getChildAt(i);
                    if (c.isShown() && c.getWidth() > 0 && c.getClass().getName().contains("ComposeView")) {
                        return c;
                    }
                }
            }
        }
        return null;
    }

    private static boolean hitView(View v, float x, float y) {
        if (v == null || !v.isShown() || v.getWidth() <= 0) return false;
        v.getLocationOnScreen(sLoc);
        float slack = 4f * v.getResources().getDisplayMetrics().density;
        return x >= sLoc[0] - slack && x <= sLoc[0] + v.getWidth() + slack
                && y >= sLoc[1] - slack && y <= sLoc[1] + v.getHeight() + slack;
    }

    private static boolean hitCutout(View root, float x, float y) {
        try {
            WindowInsets wi = root.getRootWindowInsets();
            DisplayCutout dc = wi != null ? wi.getDisplayCutout() : null;
            if (dc == null) return false;
            float slack = 14f * root.getResources().getDisplayMetrics().density;
            for (Rect r : dc.getBoundingRects()) {
                if (r.top > 0 || r.isEmpty()) continue;
                if (x >= r.left - slack && x <= r.right + slack && y <= r.bottom + slack) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    // ------------------------------------------------------------------ 7. Restart & Pref Observers

    private static void registerRestartTriggers(Context ctx) {
        try {
            ctx.getContentResolver().registerContentObserver(
                    Settings.Global.CONTENT_URI,
                    true,
                    new ContentObserver(MAIN) {
                        @Override
                        public void onChange(boolean selfChange, Uri uri) {
                            if (uri != null) {
                                String seg = uri.getLastPathSegment();
                                if (seg != null && !seg.isEmpty()
                                        && !"global".equals(seg)
                                        && !seg.startsWith(GLOBAL_PREFIX)) {
                                    return;
                                }
                            }
                            refreshPrefCache(sModule);
                            syncStatusBar();
                        }
                    });
        } catch (Throwable t) {
            Log.w(TAG, "Settings.Global pref observer failed: " + t.getMessage());
        }

        try {
            final long initialVal = Settings.Global.getLong(ctx.getContentResolver(), CMD_RESTART_SYSUI, 0L);
            ctx.getContentResolver().registerContentObserver(
                    Settings.Global.getUriFor(CMD_RESTART_SYSUI),
                    false,
                    new ContentObserver(MAIN) {
                        @Override
                        public void onChange(boolean selfChange) {
                            long currentVal = Settings.Global.getLong(ctx.getContentResolver(), CMD_RESTART_SYSUI, 0L);
                            if (currentVal != initialVal && currentVal > 0L) {
                                Log.i(TAG, "Restarting SystemUI via Settings.Global observer");
                                MAIN.postDelayed(() -> android.os.Process.killProcess(android.os.Process.myPid()), 200L);
                            }
                        }
                    });
        } catch (Throwable t) {
            Log.w(TAG, "Settings.Global restart observer failed: " + t.getMessage());
        }
    }

    // ------------------------------------------------------------------ reflection utils

    private static Field findField(Class<?> cls, String name) {
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    private static Method findMethod(Class<?> cls, String name, Class<?>... paramTypes) {
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod(name, paramTypes);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
            }
        }
        try {
            return cls.getMethod(name, paramTypes);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static void setFieldInt(Object target, String name, int value) {
        if (target == null) return;
        Field f = findField(target.getClass(), name);
        if (f == null) return;
        try {
            f.setInt(target, value);
        } catch (Throwable ignored) {
        }
    }
}
