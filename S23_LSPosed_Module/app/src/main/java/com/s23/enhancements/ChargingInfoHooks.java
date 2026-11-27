package com.s23.enhancements;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Adds a live charging-details line (W · mA · V · °C) below One UI's stock
 * charging percentage and time-to-full text on both the lock screen Keyguard
 * and the Always-On Display (AOD).
 */
final class ChargingInfoHooks {

    private static final String TAG = "S23Enhancer";
    static final String PREF_LOCKSCREEN_CHARGING_INFO = "pref_lockscreen_charging_info";

    private static final String KEYGUARD_LINE_TAG = "s23_keyguard_charging_details";
    private static final String AOD_LINE_TAG = "s23_aod_charging_details";
    private static final String AOD_PKG = "com.samsung.android.app.aodservice";
    private static final String SYSUI_PKG = "com.android.systemui";
    private static final long REFRESH_INTERACTIVE_MS = 2_500L;
    private static final long REFRESH_AOD_MS = 5_000L;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Set<Class<?>> sHookedAodClasses =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<View, Monitor> sMonitors = new WeakHashMap<>();

    private static volatile boolean sSysUiInstalled;

    private ChargingInfoHooks() {
    }

    static synchronized void installInSystemUI(HookEntry module, ClassLoader cl) {
        if (sSysUiInstalled || module == null || cl == null) return;
        sSysUiInstalled = true;

        try {
            Class<?> ctrlCls = cl.loadClass("com.android.systemui.statusbar.KeyguardIndicationController");
            module.hookAllMethods(ctrlCls, "setIndicationArea", chain -> {
                Object res = chain.proceed();
                if (!chain.getArgs().isEmpty() && chain.getArg(0) instanceof ViewGroup) {
                    try {
                        attachKeyguardLine(module, (ViewGroup) chain.getArg(0));
                    } catch (Throwable t) {
                        Log.w(TAG, "ChargingInfo keyguard inject failed: " + t.getMessage());
                    }
                }
                return res;
            });
            Log.i(TAG, "Hooked KeyguardIndicationController#setIndicationArea");
        } catch (Throwable t) {
            Log.w(TAG, "KeyguardIndicationController hook failed: " + t.getMessage());
        }

        try {
            Class<?> aodMgrCls = cl.loadClass("com.android.systemui.doze.PluginAODManager");
            module.hookAllMethods(aodMgrCls, "setAODPlugin", chain -> {
                Object res = chain.proceed();
                Object plugin = !chain.getArgs().isEmpty() ? chain.getArg(0) : null;
                if (plugin != null) {
                    hookAodChargingView(module, plugin.getClass().getClassLoader());
                }
                return res;
            });
            Log.i(TAG, "Hooked PluginAODManager#setAODPlugin");
        } catch (Throwable t) {
            Log.w(TAG, "PluginAODManager hook failed: " + t.getMessage());
        }
    }

    static void installInAod(HookEntry module, ClassLoader cl) {
        hookAodChargingView(module, cl);
    }

    private static void hookAodChargingView(HookEntry module, ClassLoader cl) {
        if (module == null || cl == null) return;
        try {
            Class<?> viewCls = Class.forName(
                    "com.samsung.android.uniform.widget.charginginfo.ChargingInfoView", false, cl);
            synchronized (sHookedAodClasses) {
                if (!sHookedAodClasses.add(viewCls)) return;
            }

            module.hookAllMethods(viewCls, "onFinishInflate", chain -> {
                Object res = chain.proceed();
                if (chain.getThisObject() instanceof View) {
                    try {
                        attachAodLine((View) chain.getThisObject());
                    } catch (Throwable t) {
                        Log.w(TAG, "ChargingInfo AOD inject failed: " + t.getMessage());
                    }
                }
                return res;
            });
            module.hookAllMethods(viewCls, "onAttachedToWindow", chain -> {
                Object res = chain.proceed();
                if (chain.getThisObject() instanceof View) {
                    View root = (View) chain.getThisObject();
                    TextView line = ensureAodLine(root);
                    if (line != null) {
                        startMonitor(module, root, line, true);
                    }
                }
                return res;
            });
            module.hookAllMethods(viewCls, "onDetachedFromWindow", chain -> {
                if (chain.getThisObject() instanceof View) {
                    stopMonitor((View) chain.getThisObject());
                }
                return chain.proceed();
            });
            Log.i(TAG, "Hooked ChargingInfoView for AOD charging details");
        } catch (Throwable ignored) {
        }
    }

    private static void attachKeyguardLine(HookEntry module, ViewGroup area) {
        Context ctx = area.getContext();
        int areaId = resolveId(ctx, "keyguard_indication_area", SYSUI_PKG);
        if (areaId != 0) {
            View inner = area.findViewById(areaId);
            if (inner instanceof LinearLayout) {
                area = (LinearLayout) inner;
            }
        }
        if (!(area instanceof LinearLayout)) return;
        final LinearLayout container = (LinearLayout) area;

        TextView existing = container.findViewWithTag(KEYGUARD_LINE_TAG);
        final TextView line;
        if (existing != null) {
            line = existing;
        } else {
            line = createStyledLine(ctx, KEYGUARD_LINE_TAG);
            int stockId = resolveId(ctx, "keyguard_indication_text_bottom", SYSUI_PKG);
            copyStyle(container.findViewById(stockId), line);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.gravity = Gravity.CENTER_HORIZONTAL;
            lp.topMargin = dp(ctx, 2);
            container.addView(line, lp);

            container.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    startMonitor(module, container, line, false);
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    stopMonitor(container);
                }
            });
        }
        if (container.isAttachedToWindow()) {
            startMonitor(module, container, line, false);
        }
    }

    private static TextView ensureAodLine(View root) {
        TextView existing = root.findViewWithTag(AOD_LINE_TAG);
        if (existing != null) return existing;
        return attachAodLine(root);
    }

    private static TextView attachAodLine(View root) {
        Context ctx = root.getContext();
        int contId = resolveId(ctx, "common_charging_info_container", AOD_PKG);
        if (contId == 0) return null;
        View contView = root.findViewById(contId);
        if (!(contView instanceof LinearLayout)) return null;
        LinearLayout container = (LinearLayout) contView;

        TextView existing = container.findViewWithTag(AOD_LINE_TAG);
        if (existing != null) return existing;

        TextView line = createStyledLine(ctx, AOD_LINE_TAG);
        int timeId = resolveId(ctx, "common_charging_info_remaining_time_text_view", AOD_PKG);
        copyStyle(root.findViewById(timeId), line);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.topMargin = dp(ctx, 3);
        container.addView(line, lp);
        return line;
    }

    private static TextView createStyledLine(Context ctx, String tag) {
        TextView tv = new TextView(ctx);
        tv.setTag(tag);
        tv.setGravity(Gravity.CENTER);
        tv.setSingleLine(true);
        tv.setIncludeFontPadding(false);
        tv.setVisibility(View.GONE);
        tv.setTextColor(0xCCFFFFFF);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        tv.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        tv.setFontFeatureSettings("tnum");
        return tv;
    }

    private static void copyStyle(View source, TextView target) {
        if (!(source instanceof TextView)) return;
        final TextView src = (TextView) source;
        applyStyleFromSource(src, target);
        target.post(() -> {
            applyStyleFromSource(src, target);
            target.requestLayout();
        });
    }

    private static void applyStyleFromSource(TextView src, TextView target) {
        target.setTextSize(TypedValue.COMPLEX_UNIT_PX, src.getTextSize());
        target.setTextColor(src.getTextColors());
        target.setIncludeFontPadding(src.getIncludeFontPadding());
        target.setAlpha(src.getAlpha());
        target.setLetterSpacing(src.getLetterSpacing());
        target.setTextScaleX(src.getTextScaleX());
        Typeface tf = src.getTypeface();
        if (tf != null) {
            target.setTypeface(tf);
        }
        target.setFontFeatureSettings("tnum");
    }

    private static void startMonitor(HookEntry module, View root, TextView line, boolean isAod) {
        synchronized (sMonitors) {
            Monitor existing = sMonitors.get(root);
            if (existing != null && existing.active) {
                existing.refreshNow();
                return;
            }
            Monitor monitor = new Monitor(module, root, line, isAod);
            sMonitors.put(root, monitor);
            monitor.start();
        }
    }

    private static void stopMonitor(View root) {
        Monitor monitor;
        synchronized (sMonitors) {
            monitor = sMonitors.remove(root);
        }
        if (monitor != null) {
            monitor.stop();
        }
    }

    private static final class Monitor {
        private final HookEntry module;
        private final WeakReference<View> rootRef;
        private final WeakReference<TextView> lineRef;
        private final boolean isAod;
        private final BroadcastReceiver receiver;
        private final Runnable refreshTick;
        private Intent lastBattery;
        private boolean active;

        Monitor(HookEntry module, View root, TextView line, boolean isAod) {
            this.module = module;
            this.rootRef = new WeakReference<>(root);
            this.lineRef = new WeakReference<>(line);
            this.isAod = isAod;
            this.receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
                        lastBattery = intent;
                        refreshNow();
                    }
                }
            };
            this.refreshTick = new Runnable() {
                @Override
                public void run() {
                    if (!active) return;
                    MAIN.removeCallbacks(this);
                    if (!refreshNow()) return;
                    long delay = isInteractive(lineRef.get()) ? REFRESH_INTERACTIVE_MS : REFRESH_AOD_MS;
                    MAIN.postDelayed(this, delay);
                }
            };
        }

        void start() {
            View root = rootRef.get();
            if (root == null) return;
            active = true;
            try {
                Intent sticky = root.getContext().registerReceiver(
                        receiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                if (sticky != null) {
                    lastBattery = sticky;
                }
            } catch (Throwable t) {
                Log.w(TAG, "ChargingInfo registerReceiver failed: " + t.getMessage());
            }
            refreshNow();
            MAIN.removeCallbacks(refreshTick);
            MAIN.postDelayed(refreshTick, isAod ? REFRESH_AOD_MS : REFRESH_INTERACTIVE_MS);
        }

        void stop() {
            active = false;
            MAIN.removeCallbacks(refreshTick);
            View root = rootRef.get();
            if (root != null) {
                try {
                    root.getContext().unregisterReceiver(receiver);
                } catch (Throwable ignored) {
                }
            }
        }

        boolean refreshNow() {
            View root = rootRef.get();
            TextView line = lineRef.get();
            if (root == null || line == null || !root.isAttachedToWindow()) {
                return false;
            }
            if (!module.isPref(PREF_LOCKSCREEN_CHARGING_INFO, true)) {
                hide(line);
                return isAod || isInteractive(line);
            }
            if (!isAod && !isInteractive(line)) {
                return false;
            }

            Intent battery = lastBattery;
            if (battery == null) {
                hide(line);
                return false;
            }
            int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL;
            if (!charging) {
                hide(line);
                return false;
            }

            String text = buildChargingText(line.getContext(), battery);
            if (text.isEmpty()) {
                hide(line);
            } else {
                if (!text.contentEquals(line.getText())) {
                    line.setText(text);
                }
                if (line.getVisibility() != View.VISIBLE) {
                    line.setVisibility(View.VISIBLE);
                }
            }
            MAIN.removeCallbacks(refreshTick);
            long delay = isInteractive(line) ? REFRESH_INTERACTIVE_MS : REFRESH_AOD_MS;
            MAIN.postDelayed(refreshTick, delay);
            return true;
        }
    }

    private static void hide(TextView line) {
        if (!"".contentEquals(line.getText())) {
            line.setText("");
        }
        if (line.getVisibility() != View.GONE) {
            line.setVisibility(View.GONE);
        }
    }

    private static boolean isInteractive(View v) {
        if (v == null) return false;
        try {
            PowerManager pm = v.getContext().getSystemService(PowerManager.class);
            return pm == null || pm.isInteractive();
        } catch (Throwable t) {
            return true;
        }
    }

    /**
     * Reads current using Samsung's mA extras first, then BatteryManager µA/mA properties
     * (matching the verified S23 Ultra scaling threshold of 20,000), plus voltage & temperature.
     */
    static String buildChargingText(Context ctx, Intent battery) {
        long currentMa = 0L;
        int extraNow = battery.getIntExtra("current_now", 0);
        int extraAvg = battery.getIntExtra("current_avg", 0);
        if (extraNow != 0) {
            currentMa = Math.abs((long) extraNow);
        } else if (extraAvg != 0) {
            currentMa = Math.abs((long) extraAvg);
        }
        if (currentMa == 0L) {
            BatteryManager bm = (BatteryManager) ctx.getSystemService(Context.BATTERY_SERVICE);
            if (bm != null) {
                int now = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                if (now == 0 || now == Integer.MIN_VALUE) {
                    now = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE);
                }
                if (now != 0 && now != Integer.MIN_VALUE) {
                    long abs = Math.abs((long) now);
                    currentMa = abs > 20_000L ? abs / 1000L : abs;
                }
            }
        }

        int voltageMv = battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
        int tempTenths = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);

        StringBuilder sb = new StringBuilder();
        if (currentMa > 0 && voltageMv > 0) {
            double volts = voltageMv > 100 ? voltageMv / 1000.0 : voltageMv;
            double watts = (currentMa / 1000.0) * volts;
            appendPart(sb, String.format(Locale.US, "%.1f W", watts));
        }
        if (currentMa > 0) {
            appendPart(sb, currentMa + " mA");
        }
        if (voltageMv > 0) {
            double volts = voltageMv > 100 ? voltageMv / 1000.0 : voltageMv;
            appendPart(sb, String.format(Locale.US, "%.2f V", volts));
        }
        if (tempTenths > 0) {
            appendPart(sb, String.format(Locale.US, "%.1f \u00B0C", tempTenths / 10.0));
        }
        return sb.toString();
    }

    private static void appendPart(StringBuilder sb, String part) {
        if (sb.length() > 0) sb.append(" \u00B7 ");
        sb.append(part);
    }

    private static int resolveId(Context ctx, String name, String primaryPkg) {
        try {
            int id = ctx.getResources().getIdentifier(name, "id", primaryPkg);
            if (id == 0) id = ctx.getResources().getIdentifier(name, "id", ctx.getPackageName());
            if (id == 0) id = ctx.getResources().getIdentifier(name, "id", AOD_PKG);
            if (id == 0) id = ctx.getResources().getIdentifier(name, "id", SYSUI_PKG);
            return id;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static int dp(Context ctx, int value) {
        return Math.round(value * ctx.getResources().getDisplayMetrics().density);
    }
}
