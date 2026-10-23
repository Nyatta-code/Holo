package com.holo.launcher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/** Keeps a screen-off listener alive so the Holo lock screen can appear. */
public class LockService extends Service {

    private static final String CHANNEL = "holo_lock";

    private final BroadcastReceiver screenOff = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            LauncherApp app = LauncherApp.get(context);
            if (!app.prefs.lockEnabled() || !LauncherApp.canOverlay(context)) {
                return;
            }
            try {
                Intent i = new Intent(context, LockActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(i);
            } catch (Exception e) {
                DebugLog.d("can't show lock screen: " + e);
            }
        }
    };

    public static void start(Context c) {
        try {
            Intent i = new Intent(c, LockService.class);
            if (Build.VERSION.SDK_INT >= 26) {
                c.startForegroundService(i);
            } else {
                c.startService(i);
            }
        } catch (Exception e) {
            DebugLog.d("can't start lock service: " + e);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Notification n = buildNotification();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, n);
        }
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenOff, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screenOff, filter);
        }
    }

    private Notification buildNotification() {
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Lock screen",
                    NotificationManager.IMPORTANCE_MIN);
            nm.createNotificationChannel(ch);
            b = new Notification.Builder(this, CHANNEL);
        } else {
            b = new Notification.Builder(this);
        }
        return b.setSmallIcon(R.drawable.ic_apps)
                .setContentTitle("Holo lock screen is on")
                .setOngoing(true)
                .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(screenOff);
        } catch (Exception ignored) {
            // already gone
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
