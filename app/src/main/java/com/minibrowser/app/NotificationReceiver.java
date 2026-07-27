package com.minibrowser.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Handles notification button taps when the activity is alive.
 * Delegates to MainActivity.instance so all UI runs on the main thread.
 */
public class NotificationReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();

        MainActivity activity = MainActivity.instance;
        if (activity == null) {
            // App was killed — just cancel the stale notification
            android.app.NotificationManager nm =
                (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(MainActivity.NOTIFICATION_ID);
            return;
        }

        if (MainActivity.ACTION_KILL.equals(action)) {
            activity.runOnUiThread(activity::exitApp);
        } else if (MainActivity.ACTION_CHANGE_URL.equals(action)) {
            // Bring app to front first, then show dialog
            Intent bringToFront = new Intent(context, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(bringToFront);
            activity.runOnUiThread(() -> activity.showUrlDialog(false));
        }
    }
}
