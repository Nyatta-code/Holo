package com.holo.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Loads the app list and renders icons on a background thread, so the UI thread
 * only ever draws ready-made bitmaps.
 */
public class AppRepository {

    public interface Listener {
        void onAppsChanged();
    }

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    private volatile List<AppInfo> apps = Collections.emptyList();
    private volatile Map<String, AppInfo> byComponent = new HashMap<>();
    private volatile int iconPx = 0;
    private volatile long lastLoadMs = 0;
    private volatile boolean loaded = false;

    public AppRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    public void addListener(Listener l) {
        if (!listeners.contains(l)) {
            listeners.add(l);
        }
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    public List<AppInfo> getApps() {
        return apps;
    }

    public boolean isLoaded() {
        return loaded;
    }

    public long getLastLoadMs() {
        return lastLoadMs;
    }

    public int getIconPx() {
        return iconPx;
    }

    public AppInfo find(ComponentName cn) {
        return cn == null ? null : byComponent.get(cn.flattenToString());
    }

    /** Loads the list if it was never loaded or if the icon size changed. */
    public void ensureLoaded(int px) {
        if (!loaded || px != iconPx) {
            iconPx = px;
            reload();
        }
    }

    public void reload() {
        final int px = iconPx;
        if (px <= 0) {
            return;
        }
        executor.execute(new Runnable() {
            @Override
            public void run() {
                long t0 = SystemClock.uptimeMillis();
                PackageManager pm = context.getPackageManager();
                Intent query = new Intent(Intent.ACTION_MAIN, null);
                query.addCategory(Intent.CATEGORY_LAUNCHER);

                List<ResolveInfo> infos = pm.queryIntentActivities(query, 0);
                List<AppInfo> list = new ArrayList<>(infos.size());
                Map<String, AppInfo> map = new HashMap<>();
                for (ResolveInfo ri : infos) {
                    if (context.getPackageName().equals(ri.activityInfo.packageName)) {
                        continue;
                    }
                    try {
                        ComponentName cn = new ComponentName(
                                ri.activityInfo.packageName, ri.activityInfo.name);
                        AppInfo ai = new AppInfo(ri.loadLabel(pm), cn,
                                IconUtil.toBitmap(ri.loadIcon(pm), px));
                        list.add(ai);
                        map.put(cn.flattenToString(), ai);
                    } catch (Exception e) {
                        DebugLog.d("skip app: " + e);
                    }
                }
                Collections.sort(list, (a, b) -> a.labelLower.compareTo(b.labelLower));

                apps = list;
                byComponent = map;
                loaded = true;
                lastLoadMs = SystemClock.uptimeMillis() - t0;
                DebugLog.d("apps loaded: " + list.size() + " in " + lastLoadMs + " ms (icon " + px + "px)");

                main.post(new Runnable() {
                    @Override
                    public void run() {
                        for (Listener l : listeners) {
                            l.onAppsChanged();
                        }
                    }
                });
            }
        });
    }
}
