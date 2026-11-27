package com.s23.enhancements;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Debug;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import io.github.libxposed.api.XposedInterface;

/**
 * Adds configurable actions (**Force stop**, **Restart**, **Clear cache**, **Screenshot**)
 * below One UI's "App info" row inside the Recents task menu (`com.sec.android.app.launcher`).
 */
final class RecentsMenuHooks {

    private static final String TAG = "S23Enhancer";

    static final String PREF_RECENTS_FORCE_STOP = "pref_recents_force_stop";
    static final String PREF_RECENTS_RESTART = "pref_recents_restart";
    static final String PREF_RECENTS_CLEAR_CACHE = "pref_recents_clear_cache";
    static final String PREF_RECENTS_SCREENSHOT = "pref_recents_screenshot";

    private static final String TAG_FORCE_STOP = "s23_recents_forcestop";
    private static final String TAG_RESTART = "s23_recents_restart";
    private static final String TAG_CLEAR_CACHE = "s23_recents_clearcache";
    private static final String TAG_SCREENSHOT = "s23_recents_screenshot";

    private static final Set<String> MARKERS = new HashSet<>(Arrays.asList(
            TAG_FORCE_STOP, TAG_RESTART, TAG_CLEAR_CACHE, TAG_SCREENSHOT
    ));

    private static final Set<String> SKIP_PACKAGES = new HashSet<>(Arrays.asList(
            "android",
            "com.android.systemui",
            "com.sec.android.app.launcher",
            "com.android.launcher3",
            "com.s23.enhancements"
    ));

    private static final Pattern SAFE_PKG_NAME = Pattern.compile("^[a-zA-Z0-9._]+$");
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static volatile boolean sInstalled;
    private static volatile int sItemLayoutId = -1; // -1 = unresolved, 0 = absent
    private static volatile Class<?> sTaskIconViewCls;
    private static volatile Class<?> sTaskViewCls;

    private static volatile String sMenuTaskPkg;
    private static volatile int sMenuTaskId = -1;
    private static volatile long sMenuTaskTime;
    private static volatile WeakReference<View> sMenuTaskCard;

    private static final List<WeakReference<Object>> sMenuHosts = new ArrayList<>();

    private RecentsMenuHooks() {
    }

    static synchronized void install(HookEntry module, ClassLoader cl, String processPkg) {
        if (sInstalled || module == null || cl == null) return;
        if (!"com.sec.android.app.launcher".equals(processPkg)) return;
        sInstalled = true;

        try {
            sTaskIconViewCls = Class.forName(
                    "com.honeyspace.ui.honeypots.tasklist.presentation.TaskIconView", false, cl);
        } catch (Throwable t) {
            Log.w(TAG, "Recents: TaskIconView lookup failed: " + t.getMessage());
        }
        try {
            sTaskViewCls = Class.forName(
                    "com.honeyspace.ui.honeypots.tasklist.presentation.TaskView", false, cl);
        } catch (Throwable t) {
            Log.w(TAG, "Recents: TaskView lookup failed: " + t.getMessage());
        }

        // Resilient target capture: hook View#performClick AND TaskIconView#c (best-effort)
        try {
            Method performClick = View.class.getDeclaredMethod("performClick");
            module.hook(performClick).intercept(chain -> {
                Object thiz = chain.getThisObject();
                if (sTaskIconViewCls != null && sTaskIconViewCls.isInstance(thiz)) {
                    recordTaskFromIcon((View) thiz);
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            Log.w(TAG, "Recents: View#performClick hook failed: " + t.getMessage());
        }

        if (sTaskIconViewCls != null) {
            try {
                module.hookAllMethods(sTaskIconViewCls, "c", chain -> {
                    if (chain.getThisObject() instanceof View) {
                        recordTaskFromIcon((View) chain.getThisObject());
                    }
                    return chain.proceed();
                });
            } catch (Throwable ignored) {
            }
        }

        // Track PopupWindow / Dialog with WeakReference so no windows leak
        XposedInterface.Hooker recordHost = chain -> {
            Object res = chain.proceed();
            recordMenuHost(chain.getThisObject());
            return res;
        };
        try {
            module.hookAllMethods(PopupWindow.class, "showAsDropDown", recordHost);
            module.hookAllMethods(PopupWindow.class, "showAtLocation", recordHost);
            module.hookAllMethods(Dialog.class, "show", recordHost);
        } catch (Throwable t) {
            Log.w(TAG, "Recents: popup host hooks skipped: " + t.getMessage());
        }

        // Inject enabled items when One UI inflates task_menu_item_layout
        try {
            Method inflate = LayoutInflater.class.getDeclaredMethod(
                    "inflate", int.class, ViewGroup.class, boolean.class);
            module.hook(inflate).intercept(chain -> {
                Object res = chain.proceed();
                if (!(res instanceof View)) return res;
                View row = (View) res;
                if (sItemLayoutId == -1) {
                    try {
                        sItemLayoutId = row.getResources().getIdentifier(
                                "task_menu_item_layout", "layout", processPkg);
                    } catch (Throwable ignored) {
                        return res;
                    }
                }
                if (sItemLayoutId > 0
                        && !chain.getArgs().isEmpty()
                        && chain.getArg(0) instanceof Integer
                        && ((Integer) chain.getArg(0)) == sItemLayoutId) {
                    row.post(() -> insertBelowAnchor(module, row, processPkg));
                }
                return res;
            });
            Log.i(TAG, "RecentsMenuHooks installed in " + processPkg);
        } catch (Throwable t) {
            Log.w(TAG, "Recents: LayoutInflater hook failed: " + t.getMessage());
        }
    }

    private static void recordTaskFromIcon(View iconView) {
        try {
            View card = findTaskCard(iconView);
            if (card == null) return;
            Object pkgObj = card.getClass().getMethod("getTaskPackageName").invoke(card);
            if (!(pkgObj instanceof String)) return;
            String pkg = (String) pkgObj;
            if (!isValidPackageName(pkg)) return;

            sMenuTaskPkg = pkg;
            sMenuTaskTime = System.currentTimeMillis();
            sMenuTaskCard = new WeakReference<>(card);
            try {
                Object tid = card.getClass().getMethod("getFirstTaskId").invoke(card);
                sMenuTaskId = tid instanceof Number ? ((Number) tid).intValue() : -1;
            } catch (Throwable ignored) {
                sMenuTaskId = -1;
            }
        } catch (Throwable ignored) {
        }
    }

    private static void recordMenuHost(Object host) {
        if (host == null) return;
        synchronized (sMenuHosts) {
            for (int i = sMenuHosts.size() - 1; i >= 0; i--) {
                Object existing = sMenuHosts.get(i).get();
                if (existing == null || existing == host) {
                    sMenuHosts.remove(i);
                }
            }
            while (sMenuHosts.size() >= 8) {
                sMenuHosts.remove(0);
            }
            sMenuHosts.add(new WeakReference<>(host));
        }
    }

    private static void insertBelowAnchor(HookEntry mod, View row, String processPkg) {
        boolean showForceStop = mod.isPref(PREF_RECENTS_FORCE_STOP, true);
        boolean showRestart = mod.isPref(PREF_RECENTS_RESTART, true);
        boolean showClearCache = mod.isPref(PREF_RECENTS_CLEAR_CACHE, true);
        boolean showScreenshot = mod.isPref(PREF_RECENTS_SCREENSHOT, true);
        if (!showForceStop && !showRestart && !showClearCache && !showScreenshot) return;

        try {
            ViewParent pp = row.getParent();
            if (!(pp instanceof ViewGroup)) return;
            ViewGroup parent = (ViewGroup) pp;

            if ((!showForceStop || hasRow(parent, TAG_FORCE_STOP))
                    && (!showRestart || hasRow(parent, TAG_RESTART))
                    && (!showClearCache || hasRow(parent, TAG_CLEAR_CACHE))
                    && (!showScreenshot || hasRow(parent, TAG_SCREENSHOT))) {
                return;
            }

            Context ctx = parent.getContext();
            int idx = indexOfAnchor(parent, anchorTitle(ctx, processPkg));
            if (idx < 0) return;

            boolean cs = HookEntry.isCzech(ctx);
            int insertAt = idx + 1;
            if (showForceStop) {
                if (!hasRow(parent, TAG_FORCE_STOP)) {
                    parent.addView(menuRow(ctx, parent, cs ? "Vynutit zastaven\u00ED" : "Force stop", TAG_FORCE_STOP,
                            RecentsMenuHooks::onForceStop), insertAt);
                }
                insertAt++;
            }
            if (showRestart) {
                if (!hasRow(parent, TAG_RESTART)) {
                    parent.addView(menuRow(ctx, parent, cs ? "Restartovat" : "Restart", TAG_RESTART,
                            RecentsMenuHooks::onRestart), insertAt);
                }
                insertAt++;
            }
            if (showClearCache) {
                if (!hasRow(parent, TAG_CLEAR_CACHE)) {
                    parent.addView(menuRow(ctx, parent, cs ? "Vymazat mezipam\u011B\u0165" : "Clear cache", TAG_CLEAR_CACHE,
                            RecentsMenuHooks::onClearCache), insertAt);
                }
                insertAt++;
            }
            if (showScreenshot) {
                if (!hasRow(parent, TAG_SCREENSHOT)) {
                    parent.addView(menuRow(ctx, parent, cs ? "Sn\u00EDmek obrazovky" : "Screenshot", TAG_SCREENSHOT,
                            RecentsMenuHooks::onScreenshot), insertAt);
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Recents: insert failed: " + t.getMessage());
        }
    }

    private static View menuRow(
            Context ctx, ViewGroup parent, String title, String tag, View.OnClickListener action) {
        View row = LayoutInflater.from(ctx).inflate(sItemLayoutId, parent, false);
        TextView tv = findTextView(row);
        if (tv != null) tv.setText(title);
        row.setTag(tag);
        row.setOnClickListener(action);
        return row;
    }

    private static boolean hasRow(ViewGroup parent, String tag) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            if (tag.equals(parent.getChildAt(i).getTag())) return true;
        }
        return false;
    }

    private static int indexOfAnchor(ViewGroup parent, String anchorTitle) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            TextView tv = findTextView(parent.getChildAt(i));
            if (tv == null || tv.getText() == null) continue;
            String text = tv.getText().toString();
            String lower = text.toLowerCase(Locale.ROOT);
            if (anchorTitle != null
                    ? text.equals(anchorTitle)
                    : (lower.contains("app info") || lower.contains("informace o aplikaci"))) {
                return i;
            }
        }
        return -1;
    }

    private static String anchorTitle(Context ctx, String processPkg) {
        try {
            int id = ctx.getResources().getIdentifier("task_option_app_info", "string", processPkg);
            if (id != 0) return ctx.getResources().getString(id);
        } catch (Throwable ignored) {
        }
        return null;
    }

    // ------------------------------------------------------------------ Actions

    private static void onForceStop(View v) {
        String pkg = resolveTargetPackage(v);
        dismissMenuPopup();
        if (pkg == null) return;
        final Context appCtx = v.getContext().getApplicationContext();
        new Thread(() -> {
            forceStopPkg(appCtx, pkg);
            boolean cs = HookEntry.isCzech(appCtx);
            toast(appCtx, cs ? "Aplikace ukon\u010Dena" : "Force stopped");
        }, "S23ForceStop").start();
    }

    private static void onRestart(View v) {
        String pkg = resolveTargetPackage(v);
        dismissMenuPopup();
        if (pkg == null) return;
        final Context appCtx = v.getContext().getApplicationContext();
        new Thread(() -> restartPkg(appCtx, pkg), "S23Restart").start();
    }

    private static void onClearCache(View v) {
        String pkg = resolveTargetPackage(v);
        dismissMenuPopup();
        if (pkg == null) return;
        final Context appCtx = v.getContext().getApplicationContext();
        new Thread(() -> clearAppCache(appCtx, pkg), "S23ClearCache").start();
    }

    private static void onScreenshot(View v) {
        final Context ctx = v.getContext();
        final Context appCtx = ctx.getApplicationContext();
        final String pkg = resolveTargetPackage(v);
        final View card = sMenuTaskCard != null ? sMenuTaskCard.get() : null;
        final int taskId = sMenuTaskId;
        dismissMenuPopup();
        if (card == null) {
            Log.w(TAG, "Recents screenshot: no task card captured");
            return;
        }
        Bitmap snapshot = extractSceneBitmap(card, pkg, taskId);
        if (snapshot != null) {
            final Bitmap shot = snapshot;
            new Thread(() -> saveShot(appCtx, shot, pkg), "S23Screenshot").start();
            return;
        }
        MAIN.postDelayed(() -> pixelCopyCard(ctx, appCtx, card, pkg), 350L);
    }

    // ------------------------------------------------------------------ Force Stop / Restart

    private static void forceStopPkg(Context ctx, String pkg) {
        if (!isValidPackageName(pkg)) return;
        long beforeKb = pssOf(ctx, pkg);
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.class.getMethod("forceStopPackage", String.class).invoke(am, pkg);
        } catch (Throwable t) {
            Log.w(TAG, "forceStopPackage reflection failed: " + t.getMessage());
        }
        sleepQuietly(350L);
        if (!isDead(ctx, pkg)) {
            runSu("am force-stop '" + pkg + "'");
            sleepQuietly(350L);
        }
        Log.i(TAG, "Recents force-stop pkg=" + pkg + " dead=" + isDead(ctx, pkg) + " freedKb=" + beforeKb);
    }

    private static void restartPkg(Context ctx, String pkg) {
        if (!isValidPackageName(pkg)) return;
        forceStopPkg(ctx, pkg);
        Intent launch = ctx.getPackageManager().getLaunchIntentForPackage(pkg);
        if (launch == null) {
            Log.w(TAG, "Recents restart: no launch intent for " + pkg);
            boolean cs = HookEntry.isCzech(ctx);
            toast(ctx, cs ? "Aplikaci nelze spustit" : "Cannot launch app");
            return;
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        MAIN.post(() -> {
            try {
                ctx.startActivity(launch);
                Log.i(TAG, "Recents restarted " + pkg);
            } catch (Throwable t) {
                Log.w(TAG, "Recents restart failed: " + t.getMessage());
            }
        });
    }

    // ------------------------------------------------------------------ Clear Cache

    private static void clearAppCache(Context ctx, String pkg) {
        if (!isValidPackageName(pkg)) return;

        // 1) Hidden PackageManager API (best-effort if launcher holds permission)
        try {
            PackageManager pm = ctx.getPackageManager();
            Class<?> observerCls = Class.forName("android.content.pm.IPackageDataObserver");
            Method m = pm.getClass().getMethod("deleteApplicationCacheFiles", String.class, observerCls);
            m.invoke(pm, pkg, null);
        } catch (Throwable ignored) {
        }

        // 2) Sanitized root clear strictly scoped to cache & code_cache directories (never app data)
        int userId = currentUserId();
        String script =
                "U=" + userId + "; P='" + pkg + "'; "
                + "KB=$(du -sk /data/user/\"$U\"/\"$P\"/cache /data/user/\"$U\"/\"$P\"/code_cache "
                + "/storage/emulated/\"$U\"/Android/data/\"$P\"/cache 2>/dev/null "
                + "| awk '{s+=$1} END {print s+0}'); "
                + "rm -rf /data/user/\"$U\"/\"$P\"/cache/* /data/user/\"$U\"/\"$P\"/cache/.[!.]* "
                + "/data/user/\"$U\"/\"$P\"/code_cache/* /data/user/\"$U\"/\"$P\"/code_cache/.[!.]* "
                + "/storage/emulated/\"$U\"/Android/data/\"$P\"/cache/* 2>/dev/null; "
                + "echo \"CLEARED:$KB\"";
        String out = runSu(script);
        long kb = -1L;
        if (out != null) {
            for (String line : out.split("\\r?\\n")) {
                if (line.startsWith("CLEARED:")) {
                    try {
                        kb = Long.parseLong(line.substring("CLEARED:".length()).trim());
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        Log.i(TAG, "Recents clear-cache pkg=" + pkg + " kb=" + kb);
        boolean cs = HookEntry.isCzech(ctx);
        if (kb >= 1024L) {
            toast(ctx, cs
                    ? String.format(Locale.getDefault(), "Mezipam\u011B\u0165 vymaz\u00E1na (%.1f MB)", kb / 1024.0)
                    : String.format(Locale.US, "Cache cleared (%.1f MB)", kb / 1024.0));
        } else if (kb > 0L) {
            toast(ctx, cs ? "Mezipam\u011B\u0165 vymaz\u00E1na (" + kb + " KB)" : "Cache cleared (" + kb + " KB)");
        } else if (kb == 0L) {
            toast(ctx, cs ? "Mezipam\u011B\u0165 vymaz\u00E1na" : "Cache cleared");
        } else {
            toast(ctx, cs ? "Vymaz\u00E1n\u00ED mezipam\u011Bti selhalo" : "Failed to clear cache");
        }
    }

    private static String runSu(String cmd) {
        Process p = null;
        try {
            p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            StringBuilder out = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (out.length() > 0) out.append('\n');
                    out.append(line);
                }
            }
            p.waitFor(10, TimeUnit.SECONDS);
            return out.toString();
        } catch (Throwable t) {
            Log.w(TAG, "su command failed: " + t.getMessage());
            return null;
        } finally {
            if (p != null) {
                try {
                    p.destroy();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    // ------------------------------------------------------------------ Screenshot

    private static Bitmap extractSceneBitmap(View card, String pkg, int taskId) {
        try {
            Object data = findSceneData(card, taskId);
            if (data == null) return null;
            Object thumb = data.getClass().getMethod("getThumbnail").invoke(data);
            if (!(thumb instanceof Bitmap)) return null;
            Bitmap bitmap = (Bitmap) thumb;
            if (bitmap.getConfig() == Bitmap.Config.HARDWARE) {
                bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false);
                if (bitmap == null) return null;
            }
            Log.i(TAG, "Recents snapshot for " + pkg + " " + bitmap.getWidth() + "x" + bitmap.getHeight());
            return bitmap;
        } catch (Throwable t) {
            Log.w(TAG, "Recents task snapshot unavailable: " + t.getMessage());
            return null;
        }
    }

    private static Object findSceneData(View card, int taskId) {
        View scene = snapshotView(card);
        for (Object candidate : new Object[]{scene, card}) {
            if (candidate == null) continue;
            try {
                Object data = candidate.getClass().getMethod("getTaskSceneData").invoke(candidate);
                if (data instanceof List) {
                    List<?> list = (List<?>) data;
                    Object firstNonNull = null;
                    for (Object entry : list) {
                        if (entry == null) continue;
                        if (firstNonNull == null) firstNonNull = entry;
                        if (taskId >= 0 && entryMatchesTaskId(entry, taskId)) {
                            return entry;
                        }
                    }
                    if (firstNonNull != null) return firstNonNull;
                    continue;
                }
                if (data != null) return data;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static boolean entryMatchesTaskId(Object entry, int taskId) {
        try {
            Object id = entry.getClass().getMethod("getTaskId").invoke(entry);
            if (id instanceof Number && ((Number) id).intValue() == taskId) return true;
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static void pixelCopyCard(Context activityCtx, Context appCtx, View card, String pkg) {
        try {
            final View target = snapshotView(card);
            if (target == null || target.getWidth() <= 0 || target.getHeight() <= 0) return;
            final Window window = windowOf(activityCtx);
            if (window == null) return;
            final View decor = window.getDecorView();
            final int width = decor.getWidth();
            final int height = decor.getHeight();
            if (width <= 0 || height <= 0) return;

            final Bitmap full = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            PixelCopy.request(window, full, result -> {
                try {
                    if (result != PixelCopy.SUCCESS) {
                        Log.w(TAG, "Recents PixelCopy failed: " + result);
                        toast(appCtx, HookEntry.isCzech(appCtx)
                                ? "Po\u0159\u00EDzen\u00ED sn\u00EDmku selhalo"
                                : "Screenshot failed");
                        return;
                    }
                    int[] at = new int[2];
                    target.getLocationInWindow(at);
                    int left = Math.max(0, at[0]);
                    int top = Math.max(0, at[1]);
                    int cropW = Math.min(target.getWidth(), width - left);
                    int cropH = Math.min(target.getHeight(), height - top);
                    if (cropW <= 0 || cropH <= 0) return;
                    final Bitmap shot = Bitmap.createBitmap(full, left, top, cropW, cropH);
                    new Thread(() -> saveShot(appCtx, shot, pkg), "S23Screenshot").start();
                } finally {
                    full.recycle();
                }
            }, MAIN);
        } catch (Throwable t) {
            Log.w(TAG, "Recents PixelCopy error: " + t.getMessage());
            toast(appCtx, HookEntry.isCzech(appCtx)
                    ? "Po\u0159\u00EDzen\u00ED sn\u00EDmku selhalo"
                    : "Screenshot failed");
        }
    }

    private static void saveShot(Context ctx, Bitmap bitmap, String pkg) {
        String name = "Screenshot_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                .format(new Date()) + ".png";
        Uri uri = null;
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            values.put(MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/Screenshots");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);

            uri = ctx.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                Log.w(TAG, "Recents screenshot: MediaStore returned null uri");
                toast(ctx, HookEntry.isCzech(ctx)
                        ? "Po\u0159\u00EDzen\u00ED sn\u00EDmku selhalo"
                        : "Screenshot failed");
                return;
            }
            try (OutputStream out = ctx.getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new java.io.IOException("Null stream for " + uri);
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    throw new java.io.IOException("Bitmap compress failed");
                }
            }
            ContentValues done = new ContentValues();
            done.put(MediaStore.Images.Media.IS_PENDING, 0);
            ctx.getContentResolver().update(uri, done, null, null);
            Log.i(TAG, "Recents screenshot saved " + uri + " for " + pkg);
            toast(ctx, HookEntry.isCzech(ctx)
                    ? "Sn\u00EDmek obrazovky ulo\u017Een"
                    : "Screenshot saved");
        } catch (Throwable t) {
            if (uri != null) {
                try {
                    ctx.getContentResolver().delete(uri, null, null);
                } catch (Throwable ignored) {
                }
            }
            Log.w(TAG, "Recents screenshot save failed: " + t.getMessage());
            toast(ctx, HookEntry.isCzech(ctx)
                    ? "Po\u0159\u00EDzen\u00ED sn\u00EDmku selhalo"
                    : "Screenshot failed");
        } finally {
            bitmap.recycle();
        }
    }

    // ------------------------------------------------------------------ Helpers

    private static String resolveTargetPackage(View v) {
        String pkg = pkgFromChain(v);
        if (pkg == null && System.currentTimeMillis() - sMenuTaskTime < 60_000L) {
            pkg = sMenuTaskPkg;
        }
        if (pkg == null) {
            pkg = topRecentPkg(v.getContext());
        }
        return isValidPackageName(pkg) ? pkg : null;
    }

    private static int currentUserId() {
        try {
            Object id = android.os.UserHandle.class.getMethod("myUserId").invoke(null);
            if (id instanceof Number) {
                int v = ((Number) id).intValue();
                if (v >= 0) return v;
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    static boolean isValidPackageName(String pkg) {
        return pkg != null
                && !pkg.isEmpty()
                && !pkg.contains("..")
                && SAFE_PKG_NAME.matcher(pkg).matches()
                && !SKIP_PACKAGES.contains(pkg);
    }

    private static View findTaskCard(View v) {
        if (sTaskViewCls == null) return null;
        View cur = v;
        for (int d = 0; d < 12 && cur != null; d++) {
            if (sTaskViewCls.isInstance(cur)) return cur;
            ViewParent p = cur.getParent();
            cur = p instanceof View ? (View) p : null;
        }
        return null;
    }

    private static void dismissMenuPopup() {
        try {
            synchronized (sMenuHosts) {
                for (int i = sMenuHosts.size() - 1; i >= 0; i--) {
                    Object h = sMenuHosts.get(i).get();
                    if (h == null) {
                        sMenuHosts.remove(i);
                        continue;
                    }
                    try {
                        if (h instanceof PopupWindow) {
                            PopupWindow pw = (PopupWindow) h;
                            if (pw.isShowing() && containsAnyMarker(pw.getContentView())) {
                                pw.dismiss();
                            }
                        } else if (h instanceof Dialog) {
                            Dialog d = (Dialog) h;
                            if (d.isShowing()) {
                                Window w = d.getWindow();
                                if (w != null && containsAnyMarker(w.getDecorView())) {
                                    d.dismiss();
                                }
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean containsAnyMarker(View v) {
        if (v == null) return false;
        Object tag = v.getTag();
        if (tag instanceof String && MARKERS.contains(tag)) return true;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                if (containsAnyMarker(g.getChildAt(i))) return true;
            }
        }
        return false;
    }

    private static View snapshotView(View card) {
        if (!(card instanceof ViewGroup)) return card;
        ViewGroup group = (ViewGroup) card;
        View best = null;
        long bestArea = 0;
        View bestImage = null;
        long bestImageArea = 0;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE) continue;
            long area = (long) child.getWidth() * child.getHeight();
            if (area > bestArea) {
                bestArea = area;
                best = child;
            }
            if (child instanceof ImageView && area > bestImageArea) {
                bestImageArea = area;
                bestImage = child;
            }
        }
        return bestImage != null ? bestImage : best;
    }

    private static Window windowOf(Context ctx) {
        Context c = ctx;
        while (c instanceof ContextWrapper) {
            if (c instanceof Activity) return ((Activity) c).getWindow();
            c = ((ContextWrapper) c).getBaseContext();
        }
        return null;
    }

    private static TextView findTextView(View v) {
        if (v instanceof TextView) return (TextView) v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                TextView t = findTextView(g.getChildAt(i));
                if (t != null) return t;
            }
        }
        return null;
    }

    private static String pkgFromChain(View v) {
        View cur = v;
        for (int d = 0; d < 12 && cur != null; d++) {
            String pkg = pkgFromObj(cur.getTag());
            if (isValidPackageName(pkg)) return pkg;
            ViewParent p = cur.getParent();
            cur = p instanceof View ? (View) p : null;
        }
        return null;
    }

    private static String pkgFromObj(Object o) {
        if (o == null) return null;
        if (o instanceof String) {
            String s = (String) o;
            return MARKERS.contains(s) ? null : s;
        }
        if (o instanceof ComponentName) return ((ComponentName) o).getPackageName();
        for (String field : new String[]{"packageName"}) {
            try {
                Object val = o.getClass().getField(field).get(o);
                if (val instanceof String) return (String) val;
            } catch (Throwable ignored) {
            }
        }
        for (String method : new String[]{"getPackageName", "getPackage"}) {
            try {
                Object val = o.getClass().getMethod(method).invoke(o);
                if (val instanceof String) return (String) val;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    @SuppressWarnings("deprecation")
    private static String topRecentPkg(Context ctx) {
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RecentTaskInfo> recents = am.getRecentTasks(20, 0);
            if (recents != null) {
                for (ActivityManager.RecentTaskInfo t : recents) {
                    ComponentName c = t.baseIntent != null ? t.baseIntent.getComponent() : null;
                    if (c == null) continue;
                    String pkg = c.getPackageName();
                    if (isValidPackageName(pkg)) return pkg;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static long pssOf(Context ctx, String pkg) {
        long total = 0L;
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningAppProcessInfo> procs = am.getRunningAppProcesses();
            if (procs == null) return 0L;
            List<Integer> pids = new ArrayList<>();
            for (ActivityManager.RunningAppProcessInfo p : procs) {
                if (p.pkgList == null) continue;
                for (String name : p.pkgList) {
                    if (pkg.equals(name)) {
                        pids.add(p.pid);
                        break;
                    }
                }
            }
            if (pids.isEmpty()) return 0L;
            int[] arr = new int[pids.size()];
            for (int i = 0; i < pids.size(); i++) arr[i] = pids.get(i);
            Debug.MemoryInfo[] infos = am.getProcessMemoryInfo(arr);
            if (infos != null) {
                for (Debug.MemoryInfo mi : infos) {
                    if (mi != null) total += mi.getTotalPss();
                }
            }
        } catch (Throwable ignored) {
        }
        return total;
    }

    private static boolean isDead(Context ctx, String pkg) {
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningAppProcessInfo> procs = am.getRunningAppProcesses();
            if (procs == null) return true;
            for (ActivityManager.RunningAppProcessInfo p : procs) {
                if (p.pkgList == null) continue;
                for (String name : p.pkgList) {
                    if (pkg.equals(name)) return false;
                }
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void toast(Context ctx, String msg) {
        MAIN.post(() -> {
            try {
                Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show();
            } catch (Throwable ignored) {
            }
        });
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
