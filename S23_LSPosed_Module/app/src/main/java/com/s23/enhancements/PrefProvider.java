package com.s23.enhancements;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import java.util.Map;

public class PrefProvider extends ContentProvider {
    public static final String AUTHORITY = "com.s23.enhancements.prefs";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY);
    public static final String PREF_NAME = "s23_preferences";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Context context = getContext();
        if (context == null) return null;

        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Bundle result = new Bundle();

        if ("getPref".equals(method) && arg != null) {
            boolean defVal = extras != null && extras.getBoolean("default", true);
            result.putBoolean("value", prefs.getBoolean(arg, defVal));
            return result;
        } else if ("getAll".equals(method)) {
            for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
                if (entry.getValue() instanceof Boolean) {
                    result.putBoolean(entry.getKey(), (Boolean) entry.getValue());
                }
            }
            return result;
        }

        return super.call(method, arg, extras);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return "vnd.android.cursor.dir/vnd.s23.preferences";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
