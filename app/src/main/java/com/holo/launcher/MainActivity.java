package com.holo.launcher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Holo-style home screen: search bar, big clock, a 5-slot dock and a
 * full-screen app drawer with live search.
 */
public class MainActivity extends Activity {

    private View home;
    private View drawer;
    private EditText searchField;
    private GridView appsGrid;
    private LinearLayout dock;
    private AppAdapter adapter;

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            loadApps();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Draw behind the status bar so the wallpaper shows through.
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        setContentView(R.layout.activity_main);

        home = findViewById(R.id.home);
        drawer = findViewById(R.id.drawer);
        searchField = findViewById(R.id.search_field);
        appsGrid = findViewById(R.id.apps_grid);
        dock = findViewById(R.id.dock);

        adapter = new AppAdapter(this);
        appsGrid.setAdapter(adapter);

        appsGrid.setOnItemClickListener((parent, view, position, id) ->
                launchApp(adapter.getItem(position)));
        appsGrid.setOnItemLongClickListener((parent, view, position, id) -> {
            showAppMenu(view, adapter.getItem(position));
            return true;
        });

        searchField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });
        searchField.setOnEditorActionListener((v, actionId, event) -> {
            if (adapter.getCount() > 0) {
                launchApp(adapter.getItem(0));
            }
            return true;
        });

        findViewById(R.id.search_bar).setOnClickListener(v -> openDrawer(true));

        home.setOnLongClickListener(v -> {
            try {
                startActivity(Intent.createChooser(
                        new Intent(Intent.ACTION_SET_WALLPAPER), getString(R.string.wallpaper)));
            } catch (Exception ignored) {
                // No wallpaper picker available.
            }
            return true;
        });

        buildDock();
        loadApps();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        registerReceiver(packageReceiver, filter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(packageReceiver);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home button pressed while we are already on top: go back to the home page.
        if (Intent.ACTION_MAIN.equals(intent.getAction())) {
            closeDrawer();
        }
    }

    @Override
    public void onBackPressed() {
        if (isDrawerOpen()) {
            closeDrawer();
        }
        // On the home screen, Back does nothing (like a real launcher).
    }

    // ---------------------------------------------------------------- drawer

    private boolean isDrawerOpen() {
        return drawer.getVisibility() == View.VISIBLE;
    }

    private void openDrawer(boolean focusSearch) {
        searchField.setText("");
        drawer.setAlpha(0f);
        drawer.setVisibility(View.VISIBLE);
        drawer.animate().alpha(1f).setDuration(180).start();
        if (focusSearch) {
            searchField.post(() -> {
                searchField.requestFocus();
                InputMethodManager imm =
                        (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(searchField, InputMethodManager.SHOW_IMPLICIT);
                }
            });
        }
    }

    private void closeDrawer() {
        if (!isDrawerOpen()) {
            return;
        }
        hideKeyboard();
        drawer.animate().alpha(0f).setDuration(150).withEndAction(() -> {
            if (drawer.getAlpha() == 0f) {
                drawer.setVisibility(View.GONE);
            }
        }).start();
    }

    private void hideKeyboard() {
        InputMethodManager imm =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(searchField.getWindowToken(), 0);
        }
        searchField.clearFocus();
    }

    // ------------------------------------------------------------------ apps

    private void loadApps() {
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            Intent main = new Intent(Intent.ACTION_MAIN, null);
            main.addCategory(Intent.CATEGORY_LAUNCHER);

            List<AppInfo> apps = new ArrayList<>();
            for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
                if (getPackageName().equals(ri.activityInfo.packageName)) {
                    continue; // hide ourselves
                }
                ComponentName cn =
                        new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name);
                apps.add(new AppInfo(ri.loadLabel(pm), cn, ri.loadIcon(pm)));
            }
            Collections.sort(apps, (a, b) ->
                    a.label.toString().compareToIgnoreCase(b.label.toString()));

            runOnUiThread(() -> adapter.setApps(apps));
        }).start();
    }

    private void launchApp(AppInfo app) {
        launch(app.launchIntent());
        closeDrawer();
    }

    private void launch(Intent intent) {
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Can't open", Toast.LENGTH_SHORT).show();
        }
    }

    private void showAppMenu(View anchor, AppInfo app) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, R.string.app_info);
        menu.getMenu().add(0, 2, 1, R.string.uninstall);
        menu.setOnMenuItemClickListener(item -> {
            Uri pkg = Uri.parse("package:" + app.packageName);
            if (item.getItemId() == 1) {
                launch(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg));
            } else {
                launch(new Intent(Intent.ACTION_DELETE, pkg));
            }
            return true;
        });
        menu.show();
    }

    // ------------------------------------------------------------------ dock

    private void buildDock() {
        dock.removeAllViews();

        addShortcut(new Intent(Intent.ACTION_DIAL));
        addShortcut(categoryIntent(Intent.CATEGORY_APP_MESSAGING));

        // Centre slot: the app drawer button.
        ImageView apps = new ImageView(this);
        apps.setImageResource(R.drawable.ic_apps);
        styleDockItem(apps);
        apps.setOnClickListener(v -> openDrawer(false));
        dock.addView(apps, dockParams());

        addShortcut(categoryIntent(Intent.CATEGORY_APP_BROWSER));
        addShortcut(new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA));
    }

    private Intent categoryIntent(String category) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(category);
        return intent;
    }

    private void addShortcut(Intent base) {
        PackageManager pm = getPackageManager();

        ResolveInfo ri = pm.resolveActivity(base, 0);
        if (ri == null || ri.activityInfo == null
                || "android".equals(ri.activityInfo.packageName)) {
            // No default app chosen (we got the system chooser): take the first match.
            List<ResolveInfo> all = pm.queryIntentActivities(base, 0);
            ri = all.isEmpty() ? null : all.get(0);
        }

        if (ri == null) {
            // Keep the layout balanced with an empty slot.
            dock.addView(new View(this), dockParams());
            return;
        }

        Drawable icon = ri.loadIcon(pm);
        final ComponentName cn =
                new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name);

        ImageView view = new ImageView(this);
        view.setImageDrawable(icon);
        styleDockItem(view);
        view.setOnClickListener(v -> {
            Intent intent = new Intent(base);
            intent.setComponent(cn);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            launch(intent);
        });
        dock.addView(view, dockParams());
    }

    private LinearLayout.LayoutParams dockParams() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
    }

    private void styleDockItem(ImageView view) {
        int pad = dp(12);
        view.setPadding(pad, pad, pad, pad);
        TypedValue tv = new TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true)) {
            view.setBackgroundResource(tv.resourceId);
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
