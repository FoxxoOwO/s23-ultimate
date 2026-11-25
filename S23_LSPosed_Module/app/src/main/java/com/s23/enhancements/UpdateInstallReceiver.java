package com.s23.enhancements;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.util.Log;
import android.widget.Toast;

/**
 * Receives PackageInstaller commit status callbacks for self-updates.
 */
public final class UpdateInstallReceiver extends BroadcastReceiver {

    private static final String TAG = "S23Enhancer";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @SuppressWarnings("deprecation")
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    context.startActivity(confirm);
                } catch (Throwable t) {
                    Log.w(TAG, "Failed to launch package install prompt: " + t.getMessage());
                }
            }
        } else if (status == PackageInstaller.STATUS_SUCCESS) {
            Toast.makeText(context, R.string.update_installed, Toast.LENGTH_SHORT).show();
        } else if (status != PackageInstaller.STATUS_FAILURE_ABORTED) {
            String msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            Log.w(TAG, "Update install failed (" + status + "): " + msg);
            Toast.makeText(context,
                    context.getString(R.string.update_failed, msg != null ? msg : ("status " + status)),
                    Toast.LENGTH_LONG).show();
        }
    }
}
