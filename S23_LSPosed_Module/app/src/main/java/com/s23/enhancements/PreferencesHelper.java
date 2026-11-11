package com.s23.enhancements;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

public class PreferencesHelper {
    public static final String PACKAGE_NAME = "com.s23.enhancements";
    public static final String PREF_NAME = "s23_preferences";
    private static final Uri CONTENT_URI = Uri.parse("content://com.s23.enhancements.prefs");

    private static volatile Context sContext;

    public static void setAppContext(Context context) {
        if (context != null && sContext == null) {
            sContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        }
    }

    public static boolean isEnabled(String key, boolean defaultValue) {
        if (key == null) return defaultValue;

        if (sContext != null) {
            try {
                ContentResolver resolver = sContext.getContentResolver();
                if (resolver != null) {
                    Bundle extras = new Bundle();
                    extras.putBoolean("default", defaultValue);
                    Bundle res = resolver.call(CONTENT_URI, "getPref", key, extras);
                    if (res != null) {
                        return res.getBoolean("value", defaultValue);
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        return defaultValue;
    }

    public static boolean isEnabled(Context context, String key, boolean defaultValue) {
        if (context != null) {
            setAppContext(context);
        }
        return isEnabled(key, defaultValue);
    }
}
