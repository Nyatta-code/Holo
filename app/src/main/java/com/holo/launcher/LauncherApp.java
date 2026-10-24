package com.holo.launcher;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.provider.Settings;

import java.io.PrintWriter;
import java.io.StringWriter;

/** Process-wide singletons: settings, app list and home layout. */
public class LauncherApp extends Application {

    public Prefs prefs;
    public AppRepository repo;
    public LayoutStore store;

    public static LauncherApp get(Context c) {
        return (LauncherApp) c.getApplicationContext();
    }

    private void installCrashHandler() {
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                StringWriter sw = new StringWriter();
                error.printStackTrace(new PrintWriter(sw));
                String trace = "Thread: " + thread.getName() + "\n"
                        + Build.MANUFACTURER + " " + Build.MODEL + ", Android "
                        + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\n\n" + sw;
                Intent i = new Intent(this, CrashActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                i.putExtra("trace", trace);
                startActivity(i);
                Thread.sleep(600);
            } catch (Throwable ignored) {
                // nothing more we can do
            }
            if (previous != null) {
                previous.uncaughtException(thread, error);
            } else {
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(10);
            }
        });
    }

    /**
     * Some Android 12 builds (seen on Xiaomi) crash every app with an empty Toast inside
     * android.os.GraphicsEnvironment. That is not our bug, so swallow exactly that
     * error and keep the main loop running; anything else still reaches the crash screen.
     */
    private void installMainLoopGuard() {
        new Handler(Looper.getMainLooper()).post(() -> {
            while (true) {
                try {
                    Looper.loop();
                    return;
                } catch (Throwable t) {
                    if (isSystemToastBug(t)) {
                        DebugLog.d("ignored system error: " + t);
                        continue;
                    }
                    if (t instanceof RuntimeException) {
                        throw (RuntimeException) t;
                    }
                    if (t instanceof Error) {
                        throw (Error) t;
                    }
                    throw new RuntimeException(t);
                }
            }
        });
    }

    private static boolean isSystemToastBug(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            String m = String.valueOf(c.getMessage());
            if (m.contains("ANGLE_FOR_ANDROID_TOAST_MESSAGE")) {
                return true;
            }
            for (StackTraceElement e : c.getStackTrace()) {
                if (e.getClassName().startsWith("android.os.GraphicsEnvironment")) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 28 && Application.getProcessName().endsWith(":crash")) {
            return; // the crash report screen runs in its own light process
        }
        installCrashHandler();
        installMainLoopGuard();
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
