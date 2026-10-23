package com.holo.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restarts the lock screen service after a reboot. */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        LauncherApp app = LauncherApp.get(context);
        if (app.prefs.lockEnabled() && LauncherApp.canOverlay(context)) {
            LockService.start(context);
        }
    }
}
