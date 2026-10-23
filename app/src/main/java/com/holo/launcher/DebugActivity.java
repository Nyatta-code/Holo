package com.holo.launcher;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.appwidget.AppWidgetHost;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/** Private debug menu: live performance numbers, log, and maintenance actions. */
public class DebugActivity extends Activity {

    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextView stats;
    private TextView log;
    private LauncherApp app;
    private Button fpsButton;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refresh();
            ui.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        app = LauncherApp.get(this);
        float d = getResources().getDisplayMetrics().density;
        int pad = (int) (12 * d);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        stats = new TextView(this);
        stats.setTypeface(Typeface.MONOSPACE);
        stats.setTextSize(12);
        stats.setTextColor(0xFF33B5E5);
        root.addView(stats);

        addButton(root, "Reload apps", () -> {
            app.repo.reload();
            toast("Reloading apps");
        });
        fpsButton = addButton(root, "", () -> {
            app.prefs.setBool("debug_fps", !app.prefs.debugFps());
            updateFpsButton();
        });
        addButton(root, "Force garbage collection", () -> {
            System.gc();
            DebugLog.d("manual GC");
        });
        addButton(root, "Copy log", () -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("Holo log", DebugLog.dump()));
                toast("Log copied");
            }
        });
        addButton(root, "Clear log", () -> DebugLog.clear());
        addButton(root, "Reset home layout", () -> new AlertDialog.Builder(this)
                .setTitle("Reset home layout?")
                .setPositiveButton("Reset", (dlg, w) -> {
                    AppWidgetHost host = new AppWidgetHost(this, 1024);
                    for (int id : app.store.widgetIds()) {
                        host.deleteAppWidgetId(id);
                    }
                    app.store.reset();
                    DebugLog.d("layout reset");
                    toast("Done");
                })
                .setNegativeButton("Cancel", null)
                .show());
        addButton(root, "Restart launcher process", () -> Process.killProcess(Process.myPid()));
        addButton(root, "Hide debug menu", () -> {
            app.prefs.setBool("debug_enabled", false);
            finish();
        });

        log = new TextView(this);
        log.setTypeface(Typeface.MONOSPACE);
        log.setTextSize(10);
        log.setTextColor(0xCCFFFFFF);
        log.setPadding(0, pad, 0, 0);
        root.addView(log);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
        updateFpsButton();
    }

    private Button addButton(LinearLayout parent, String text, final Runnable action) {
        Button b = new Button(this);
        b.setText(text);
        b.setOnClickListener((View v) -> action.run());
        parent.addView(b, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return b;
    }

    private void updateFpsButton() {
        fpsButton.setText(app.prefs.debugFps() ? "FPS overlay: ON" : "FPS overlay: OFF");
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        FpsMeter.get().start();
        ui.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(tick);
        FpsMeter.get().stop();
    }

    private void refresh() {
        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long maxMb = rt.maxMemory() / (1024 * 1024);
        long nativeMb = Debug.getNativeHeapAllocatedSize() / (1024 * 1024);

        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        long availMb = 0;
        if (am != null) {
            am.getMemoryInfo(mi);
            availMb = mi.availMem / (1024 * 1024);
        }

        float refreshRate = 0f;
        try {
            Display display = getWindowManager().getDefaultDisplay();
            refreshRate = display.getRefreshRate();
        } catch (Exception ignored) {
            // leave 0
        }

        StringBuilder sb = new StringBuilder();
        FpsMeter m = FpsMeter.get();
        sb.append(String.format(Locale.US, "FPS            %.0f / %.0f Hz\n", m.getFps(), refreshRate));
        sb.append(String.format(Locale.US, "Janky frames   %d%%\n", m.getJankPercent()));
        sb.append(String.format(Locale.US, "Java heap      %d / %d MB\n", usedMb, maxMb));
        sb.append(String.format(Locale.US, "Native heap    %d MB\n", nativeMb));
        sb.append(String.format(Locale.US, "Free RAM       %d MB\n", availMb));
        sb.append(String.format(Locale.US, "Apps           %d (loaded in %d ms)\n",
                app.repo.getApps().size(), app.repo.getLastLoadMs()));
        sb.append(String.format(Locale.US, "Icon size      %d px\n", app.repo.getIconPx()));
        sb.append(String.format(Locale.US, "Home items     %d\n", app.store.items().size()));
        sb.append(String.format(Locale.US, "Grid / pages   %dx%d / %d\n",
                app.prefs.cols(), app.prefs.rows(), app.prefs.pages()));
        sb.append(String.format(Locale.US, "Device         %s %s\n", Build.MANUFACTURER, Build.MODEL));
        sb.append(String.format(Locale.US, "Android        %s (API %d)\n",
                Build.VERSION.RELEASE, Build.VERSION.SDK_INT));
        stats.setText(sb.toString());
        log.setText(DebugLog.dump());
    }
}
