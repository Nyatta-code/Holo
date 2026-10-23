package com.holo.launcher;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.preference.PreferenceManager;
import android.provider.Settings;

/** Process-wide singletons: settings, app list and home layout. */
public class LauncherApp extends Application {

    public Prefs prefs;
    public AppRepository repo;
    public LayoutStore store;

    public static LauncherApp get(Context c) {
        return (LauncherApp) c.getApplicationContext();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        prefs = new Prefs(this);
        repo = new AppRepository(this);
        store = new LayoutStore(this);

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                repo.reload();
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(receiver, filter);
        }

        if (prefs.lockEnabled() && canOverlay(this)) {
            LockService.start(this);
        }
    }

    public static boolean canOverlay(Context c) {
        return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(c);
    }
}
