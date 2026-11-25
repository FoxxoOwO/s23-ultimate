package com.s23.enhancements;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import com.s23.enhancements.ui.oneui.OneUiDialog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.HttpsURLConnection;

/**
 * Checks GitHub Releases (FoxxoOwO/s23-ultimate) for newer APK builds and installs
 * them via PackageInstaller.
 */
public final class AppUpdater {

    private static final String TAG = "S23Enhancer";
    private static final String LATEST_RELEASE_URL =
            "https://api.github.com/repos/FoxxoOwO/s23-ultimate/releases/latest";
    private static final String KEY_AUTO_UPDATE = "pref_app_auto_update";
    private static final String KEY_LAST_CHECK_MS = "pref_app_last_update_check_ms";
    private static final long CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L;
    private static final int TIMEOUT_MS = 10_000;

    private static final class ReleaseInfo {
        final String tag;
        final String body;
        final String htmlUrl;
        final String apkUrl;

        ReleaseInfo(String tag, String body, String htmlUrl, String apkUrl) {
            this.tag = tag;
            this.body = body;
            this.htmlUrl = htmlUrl;
            this.apkUrl = apkUrl;
        }
    }

    private AppUpdater() {
    }

    public static String getCurrentVersion(Context context) {
        if (context == null) return "0";
        try {
            PackageInfo pi = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return pi.versionName != null ? pi.versionName : "0";
        } catch (Throwable ignored) {
            return "0";
        }
    }

    public static void checkOnStartup(Activity activity, SharedPreferences prefs) {
        if (activity == null || prefs == null) return;
        if (!prefs.getBoolean(KEY_AUTO_UPDATE, true)) return;
        long now = System.currentTimeMillis();
        long last = prefs.getLong(KEY_LAST_CHECK_MS, 0L);
        if (now - last < CHECK_INTERVAL_MS) return;
        runCheck(activity, prefs, false);
    }

    public static void checkNow(Activity activity, SharedPreferences prefs) {
        if (activity == null) return;
        Toast.makeText(activity, R.string.update_checking, Toast.LENGTH_SHORT).show();
        runCheck(activity, prefs, true);
    }

    private static void runCheck(Activity activity, SharedPreferences prefs, boolean userInitiated) {
        final String currentVer = getCurrentVersion(activity);
        new Thread(() -> {
            try {
                ReleaseInfo info = fetchLatest(currentVer);
                if (prefs != null) {
                    prefs.edit().putLong(KEY_LAST_CHECK_MS, System.currentTimeMillis()).apply();
                }
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (isNewerVersion(info.tag, currentVer)) {
                    activity.runOnUiThread(() -> {
                        if (!activity.isFinishing() && !activity.isDestroyed()) {
                            promptUpdate(activity, info);
                        }
                    });
                } else if (userInitiated) {
                    activity.runOnUiThread(() -> Toast.makeText(activity,
                            activity.getString(R.string.update_up_to_date, currentVer),
                            Toast.LENGTH_SHORT).show());
                }
            } catch (Throwable t) {
                Log.w(TAG, "Update check failed: " + t.getMessage());
                if (userInitiated && !activity.isFinishing() && !activity.isDestroyed()) {
                    String msg = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                    activity.runOnUiThread(() -> Toast.makeText(activity,
                            activity.getString(R.string.update_failed, msg),
                            Toast.LENGTH_LONG).show());
                }
            }
        }, "s23enh-update-check").start();
    }

    private static ReleaseInfo fetchLatest(String currentVer) throws Exception {
        HttpsURLConnection conn = (HttpsURLConnection) new URL(LATEST_RELEASE_URL).openConnection();
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        conn.setRequestProperty("User-Agent", "S23Enhancer/" + currentVer);
        int code = conn.getResponseCode();
        if (code != HttpURLConnection.HTTP_OK) {
            throw new IllegalStateException("HTTP " + code);
        }
        String raw;
        try (InputStream in = conn.getInputStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            raw = out.toString(StandardCharsets.UTF_8.name());
        } finally {
            conn.disconnect();
        }

        JSONObject json = new JSONObject(raw);
        String tag = json.optString("tag_name", "").trim();
        String body = json.optString("body", "").trim();
        String htmlUrl = json.optString("html_url", "https://github.com/FoxxoOwO/s23-ultimate/releases").trim();
        String apkUrl = null;
        JSONArray assets = json.optJSONArray("assets");
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.optJSONObject(i);
                if (asset == null) continue;
                String name = asset.optString("name", "");
                if (name.endsWith(".apk")) {
                    apkUrl = asset.optString("browser_download_url", "");
                    break;
                }
            }
        }
        if (tag.isEmpty()) {
            throw new IllegalStateException("Missing release tag");
        }
        return new ReleaseInfo(tag, body, htmlUrl, apkUrl);
    }

    static boolean isNewerVersion(String remoteTag, String localVersion) {
        int[] r = parseParts(remoteTag);
        int[] l = parseParts(localVersion);
        int len = Math.max(r.length, l.length);
        for (int i = 0; i < len; i++) {
            int rv = i < r.length ? r[i] : 0;
            int lv = i < l.length ? l[i] : 0;
            if (rv != lv) return rv > lv;
        }
        return false;
    }

    private static int[] parseParts(String version) {
        if (version == null) return new int[]{0};
        String s = version.trim();
        if (s.startsWith("v") || s.startsWith("V")) s = s.substring(1);
        int dash = s.indexOf('-');
        if (dash >= 0) s = s.substring(0, dash);
        String[] tokens = s.split("\\.");
        int[] out = new int[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            try {
                out[i] = Integer.parseInt(tokens[i].replaceAll("[^0-9]", ""));
            } catch (Throwable ignored) {
                out[i] = 0;
            }
        }
        return out;
    }

    private static void promptUpdate(Activity activity, ReleaseInfo info) {
        String title = activity.getString(R.string.update_available_title, info.tag);
        String message = info.body.isEmpty() ? info.htmlUrl : info.body;
        OneUiDialog.showConfirm(
                activity,
                title,
                message,
                activity.getString(R.string.update_action_install),
                () -> startInstall(activity, info));
    }

    private static void startInstall(Activity activity, ReleaseInfo info) {
        if (info.apkUrl == null || info.apkUrl.isEmpty()) {
            Toast.makeText(activity, R.string.update_no_apk, Toast.LENGTH_SHORT).show();
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(info.htmlUrl)));
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !activity.getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(activity, R.string.update_perm_required, Toast.LENGTH_LONG).show();
            Intent perm = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(perm);
            return;
        }

        Toast.makeText(activity, R.string.update_downloading, Toast.LENGTH_SHORT).show();
        final Context appCtx = activity.getApplicationContext();
        final String currentVer = getCurrentVersion(appCtx);

        new Thread(() -> {
            PackageInstaller.Session session = null;
            HttpURLConnection conn = null;
            try {
                conn = openWithRedirects(info.apkUrl, currentVer, 5);
                int code = conn.getResponseCode();
                if (code != HttpURLConnection.HTTP_OK) {
                    throw new IllegalStateException("HTTP " + code);
                }

                long total = conn.getContentLengthLong();
                PackageInstaller pi = appCtx.getPackageManager().getPackageInstaller();
                PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                        PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                params.setAppPackageName(appCtx.getPackageName());
                int sessionId = pi.createSession(params);
                session = pi.openSession(sessionId);

                try (InputStream in = conn.getInputStream();
                     OutputStream out = session.openWrite("update.apk", 0, total > 0 ? total : -1)) {
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                    }
                    session.fsync(out);
                }

                if (!activity.isFinishing() && !activity.isDestroyed()) {
                    activity.runOnUiThread(() -> Toast.makeText(
                            activity, R.string.update_installing, Toast.LENGTH_SHORT).show());
                }

                Intent callback = new Intent(appCtx, UpdateInstallReceiver.class);
                int flags = PendingIntent.FLAG_UPDATE_CURRENT
                        | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
                PendingIntent piIntent = PendingIntent.getBroadcast(appCtx, sessionId, callback, flags);
                session.commit(piIntent.getIntentSender());
            } catch (Throwable t) {
                Log.w(TAG, "Update download/install failed: " + t.getMessage());
                if (session != null) {
                    try {
                        session.abandon();
                    } catch (Throwable ignored) {
                    }
                }
                if (!activity.isFinishing() && !activity.isDestroyed()) {
                    String msg = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                    activity.runOnUiThread(() -> Toast.makeText(activity,
                            activity.getString(R.string.update_failed, msg),
                            Toast.LENGTH_LONG).show());
                }
            } finally {
                if (conn != null) conn.disconnect();
            }
        }, "s23enh-update-dl").start();
    }

    private static HttpURLConnection openWithRedirects(String urlStr, String currentVer, int maxRedirects)
            throws Exception {
        String cur = urlStr;
        for (int i = 0; i <= maxRedirects; i++) {
            HttpURLConnection conn = (HttpURLConnection) new URL(cur).openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(30_000);
            conn.setRequestProperty("User-Agent", "S23Enhancer/" + currentVer);
            int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_MOVED_PERM
                    || code == HttpURLConnection.HTTP_MOVED_TEMP
                    || code == 307 || code == 308) {
                String loc = conn.getHeaderField("Location");
                conn.disconnect();
                if (loc == null || loc.isEmpty()) {
                    throw new IllegalStateException("Redirect without Location header");
                }
                cur = new URL(new URL(cur), loc).toString();
                continue;
            }
            return conn;
        }
        throw new IllegalStateException("Too many redirects");
    }
}
