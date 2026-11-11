package com.s23.enhancements.ui;

import android.database.ContentObserver;
import android.graphics.drawable.Icon;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;
import com.s23.enhancements.PrefProvider;
import com.s23.enhancements.R;

public class OutdoorModeTileService extends TileService {

    private static final String TAG = "OutdoorModeTile";
    private static final String KEY_OUTDOOR = "pref_display_outdoor_mode";

    private ContentObserver mObserver;

    /** The tile only works while the feature is enabled in the companion app. */
    private boolean isFeatureEnabled() {
        return getSharedPreferences(PrefProvider.PREF_NAME, MODE_PRIVATE)
                .getBoolean(KEY_OUTDOOR, true);
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTileState();

        if (mObserver == null) {
            mObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
                @Override
                public void onChange(boolean selfChange) {
                    updateTileState();
                }
            };
            try {
                getContentResolver().registerContentObserver(
                    Settings.System.getUriFor("display_outdoor_mode"),
                    false,
                    mObserver
                );
            } catch (Throwable t) {
                Log.w(TAG, "Failed to register ContentObserver", t);
            }
        }
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
        if (mObserver != null) {
            try {
                getContentResolver().unregisterContentObserver(mObserver);
            } catch (Throwable ignored) {}
            mObserver = null;
        }
    }

    @Override
    public void onClick() {
        super.onClick();
        if (!isFeatureEnabled()) {
            return;
        }
        try {
            int current = Settings.System.getInt(getContentResolver(), "display_outdoor_mode", 0);
            int next = (current == 1) ? 0 : 1;
            try {
                android.net.Uri uri = android.net.Uri.parse("content://com.android.settings.slices");
                getContentResolver().call(uri, "set_outdoor_mode", String.valueOf(next), null);
            } catch (Throwable t) {
                Log.w(TAG, "SliceProvider call failed: " + t.getMessage());
            }
            try {
                Settings.System.putInt(getContentResolver(), "display_outdoor_mode", next);
            } catch (Throwable ignored) {}
            updateTileState();
        } catch (Throwable t) {
            Log.e(TAG, "Failed to toggle outdoor mode", t);
        }
    }

    private void updateTileState() {
        Tile tile = getQsTile();
        if (tile == null) return;

        try {
            if (!isFeatureEnabled()) {
                tile.setState(Tile.STATE_UNAVAILABLE);
                tile.setLabel(getString(R.string.tile_outdoor_mode));
                tile.setIcon(Icon.createWithResource(this, R.drawable.ic_sun));
                tile.setSubtitle(getString(R.string.tile_outdoor_disabled));
                tile.updateTile();
                return;
            }

            int val = Settings.System.getInt(getContentResolver(), "display_outdoor_mode", 0);
            boolean active = (val == 1);

            tile.setState(active ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            tile.setLabel(getString(R.string.tile_outdoor_mode));
            tile.setIcon(Icon.createWithResource(this, R.drawable.ic_sun));
            tile.setSubtitle(active ? "1200 nitů" : "Vypnuto");
            tile.updateTile();
        } catch (Throwable t) {
            Log.w(TAG, "Error updating tile state", t);
        }
    }
}
