package com.holo.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.SearchManager;
import android.app.WallpaperManager;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Holo-style launcher: swipeable workspace pages with widgets, a dock, and a
 * two-tab (Apps / Widgets) drawer.
 */
public class MainActivity extends Activity implements AppRepository.Listener {

    private static final int REQ_BIND = 1001;
    private static final int REQ_CONFIGURE = 1002;
    private static final int HOST_ID = 1024;

    private LauncherApp app;
    private Prefs prefs;
    private AppRepository repo;
    private LayoutStore store;

    private ViewPager pager;
    private HoloPageIndicator indicator;
    private LinearLayout dock;
    private View drawer;
    private RecyclerView appsList;
    private RecyclerView widgetsList;
    private EditText searchField;
    private TextView tabApps;
    private TextView tabWidgets;
    private TextView fpsView;

    private AppWidgetHost host;
    private AppWidgetManager awm;
    private WallpaperManager wallpaperManager;
    private WorkspaceAdapter wsAdapter;
    private AppAdapter appAdapter;
    private WidgetAdapter widgetAdapter;

    private ItemInfo pendingMove;
    private int pendingWidgetId = -1;
    private boolean prefsDirty;
    private boolean widgetsLoaded;
    private boolean fpsRunning;

    private int cols;
    private int rows;
    private int pageCount;
    private float density;

    private final Handler ui = new Handler(Looper.getMainLooper());

    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener prefListener =
            (sp, key) -> prefsDirty = true;

    private final Runnable fpsTick = new Runnable() {
        @Override
        public void run() {
            if (!prefs.debugFps()) {
                fpsView.setVisibility(View.GONE);
                return;
            }
            FpsMeter m = FpsMeter.get();
            fpsView.setText(String.format(Locale.US, "%.0f fps  jank %d%%",
                    m.getFps(), m.getJankPercent()));
            fpsView.setVisibility(View.VISIBLE);
            ui.postDelayed(this, 500);
        }
    };

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        app = LauncherApp.get(this);
        prefs = app.prefs;
        repo = app.repo;
        store = app.store;
        density = getResources().getDisplayMetrics().density;

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        setContentView(R.layout.activity_main);

        cols = prefs.cols();
        rows = prefs.rows();
        pageCount = prefs.pages();

        host = new AppWidgetHost(this, HOST_ID);
        awm = AppWidgetManager.getInstance(this);
        wallpaperManager = WallpaperManager.getInstance(this);

        for (int id : store.reflow(pageCount, cols, rows)) {
            host.deleteAppWidgetId(id);
        }
        store.ensureDefaults(prefs.homePage(), cols);

        drawer = findViewById(R.id.drawer);
        dock = findViewById(R.id.dock);
        pager = findViewById(R.id.workspace);
        appsList = findViewById(R.id.apps_list);
        widgetsList = findViewById(R.id.widgets_list);
        searchField = findViewById(R.id.search_field);
        tabApps = findViewById(R.id.tab_apps);
        tabWidgets = findViewById(R.id.tab_widgets);
        fpsView = findViewById(R.id.fps_overlay);

        setupSearchBar();
        setupWorkspace();
        setupDock();
        setupDrawer();

        repo.ensureLoaded(iconPx());
        prefs.raw().registerOnSharedPreferenceChangeListener(prefListener);
        DebugLog.d("MainActivity created " + cols + "x" + rows + ", " + pageCount + " pages");
    }

    @Override
    protected void onStart() {
        super.onStart();
        try {
            host.startListening();
        } catch (Exception e) {
            DebugLog.d("host.startListening failed: " + e);
        }
        repo.addListener(this);
        if (repo.isLoaded()) {
            onAppsChanged();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (prefsDirty) {
            prefsDirty = false;
            recreate();
            return;
        }
        if (prefs.debugFps()) {
            FpsMeter.get().start();
            fpsRunning = true;
            ui.removeCallbacks(fpsTick);
            ui.post(fpsTick);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(fpsTick);
        if (fpsRunning) {
            FpsMeter.get().stop();
            fpsRunning = false;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        repo.removeListener(this);
        try {
            host.stopListening();
        } catch (Exception ignored) {
            // nothing to do
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        prefs.raw().unregisterOnSharedPreferenceChangeListener(prefListener);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (Intent.ACTION_MAIN.equals(intent.getAction())) {
            pendingMove = null;
            if (isDrawerOpen()) {
                closeDrawer();
            } else {
                pager.setCurrentItem(prefs.homePage(), true);
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (pendingMove != null) {
            pendingMove = null;
        } else if (isDrawerOpen()) {
            closeDrawer();
        } else if (pager.getCurrentItem() != prefs.homePage()) {
            pager.setCurrentItem(prefs.homePage(), true);
        }
        // Back on the home screen does nothing, like a real launcher.
    }

    @Override
    public void onAppsChanged() {
        appAdapter.setApps(repo.getApps());
        if (repo.isLoaded() && !repo.getApps().isEmpty()) {
            store.pruneMissing(repo);
        }
        refreshAllPages();
    }

    // ------------------------------------------------------------------ helpers

    private int dp(int v) {
        return (int) (v * density + 0.5f);
    }

    private int iconPx() {
        return Math.round(48 * prefs.iconScale() / 100f * density);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private void launch(Intent intent) {
        try {
            startActivity(intent);
        } catch (Exception e) {
            toast("Can't open");
        }
    }

    // ------------------------------------------------------------------ search bar

    private void setupSearchBar() {
        View bar = findViewById(R.id.search_bar);
        bar.setVisibility(prefs.showSearch() ? View.VISIBLE : View.GONE);
        bar.setOnClickListener(v -> openDrawer(true, false));
    }

    // ------------------------------------------------------------------ workspace

    private void setupWorkspace() {
        wsAdapter = new WorkspaceAdapter();
        pager.setAdapter(wsAdapter);
        pager.setOffscreenPageLimit(Math.max(1, pageCount));
        pager.setCurrentItem(prefs.homePage(), false);

        FrameLayout holder = findViewById(R.id.indicator_holder);
        indicator = new HoloPageIndicator(this);
        holder.addView(indicator, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        indicator.setCount(pageCount);
        indicator.setPosition(prefs.homePage());
        holder.setVisibility(prefs.showIndicator() ? View.VISIBLE : View.INVISIBLE);

        pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float offset, int offsetPixels) {
                indicator.setPosition(position + offset);
                scrollWallpaper(position + offset);
            }
        });

        String t = prefs.transition();
        if ("zoom".equals(t)) {
            pager.setPageTransformer(false, (page, pos) -> {
                if (pos < -1f || pos > 1f) {
                    page.setAlpha(0f);
                    return;
                }
                float s = Math.max(0.85f, 1f - Math.abs(pos) * 0.15f);
                page.setScaleX(s);
                page.setScaleY(s);
                page.setAlpha(1f - Math.abs(pos) * 0.5f);
            });
        } else if ("cube".equals(t)) {
            pager.setPageTransformer(false, (page, pos) -> {
                page.setPivotX(pos < 0f ? page.getWidth() : 0f);
                page.setPivotY(page.getHeight() * 0.5f);
                page.setRotationY(-35f * pos);
            });
        }
    }

    private void scrollWallpaper(float position) {
        if (!prefs.wallpaperScroll() || pageCount < 2) {
            return;
        }
        try {
            wallpaperManager.setWallpaperOffsetSteps(1f / (pageCount - 1), 1f);
            wallpaperManager.setWallpaperOffsets(pager.getWindowToken(),
                    position / (pageCount - 1), 0.5f);
        } catch (Exception ignored) {
            // some ROMs reject this; the wallpaper just doesn't scroll
        }
    }

    private class WorkspaceAdapter extends PagerAdapter {
        private final android.util.SparseArray<CellLayout> views = new android.util.SparseArray<>();

        CellLayout pageView(int page) {
            return views.get(page);
        }

        @Override
        public int getCount() {
            return pageCount;
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }

        @Override
        public Object instantiateItem(ViewGroup container, final int position) {
            CellLayout cl = new CellLayout(MainActivity.this);
            cl.setGrid(cols, rows);
            cl.setPadding(dp(6), dp(4), dp(6), dp(4));
            cl.setListener(new CellLayout.Listener() {
                @Override
                public void onEmptyLongPress() {
                    showHomeMenu();
                }

                @Override
                public void onEmptyTap(int cellX, int cellY) {
                    onEmptyCellTapped(position, cellX, cellY);
                }
            });
            fillPage(cl, position);
            container.addView(cl, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            views.put(position, cl);
            return cl;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView((View) object);
            views.remove(position);
        }
    }

    private void fillPage(CellLayout cl, int page) {
        for (ItemInfo it : store.onPage(page)) {
            View v = createItemView(it);
            if (v != null) {
                cl.addView(v, new CellLayout.LP(it));
            }
        }
    }

    private void refreshPage(int page) {
        CellLayout cl = wsAdapter.pageView(page);
        if (cl != null) {
            cl.removeAllViews();
            fillPage(cl, page);
        }
    }

    private void refreshAllPages() {
        for (int p = 0; p < pageCount; p++) {
            refreshPage(p);
        }
    }

    private View createItemView(ItemInfo it) {
        try {
            switch (it.type) {
                case ItemInfo.APP: {
                    AppInfo ai = repo.find(it.component());
                    return ai == null ? null : makeShortcut(it, ai);
                }
                case ItemInfo.WIDGET:
                    return makeWidget(it);
                case ItemInfo.CLOCK:
                    return makeClock(it);
                default:
                    return null;
            }
        } catch (Exception e) {
            DebugLog.d("createItemView failed: " + e);
            return null;
        }
    }

    private View makeShortcut(final ItemInfo it, final AppInfo ai) {
        TextView tv = new TextView(this);
        int px = iconPx();
        Drawable d = new BitmapDrawable(getResources(), ai.icon);
        d.setBounds(0, 0, px, px);
        tv.setCompoundDrawables(null, d, null, null);
        tv.setCompoundDrawablePadding(dp(4));
        tv.setGravity(Gravity.CENTER);
        tv.setMaxLines(1);
        tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tv.setTextColor(0xFFFFFFFF);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, prefs.labelSize());
        tv.setShadowLayer(3f, 0f, 1f, 0xCC000000);
        tv.setText(prefs.showLabels() ? ai.label : "");
        tv.setOnClickListener(v -> launch(ai.launchIntent()));
        tv.setOnLongClickListener(v -> {
            showItemMenu(it);
            return true;
        });
        return tv;
    }

    private View makeWidget(final ItemInfo it) {
        AppWidgetProviderInfo info = awm.getAppWidgetInfo(it.widgetId);
        if (info == null) {
            // the app that provided this widget is gone
            host.deleteAppWidgetId(it.widgetId);
            store.remove(it);
            return null;
        }
        final AppWidgetHostView hv = host.createView(this, it.widgetId, info);
        hv.setAppWidget(it.widgetId, info);

        LongPressFrame frame = new LongPressFrame(this);
        frame.addView(hv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        frame.setOnLongPress(() -> showItemMenu(it));
        frame.addOnLayoutChangeListener((v, l, t, r, b, oldL, oldT, oldR, oldB) -> {
            int w = r - l;
            int h = b - t;
            if (w > 0 && h > 0 && (w != oldR - oldL || h != oldB - oldT)) {
                int wd = Math.round(w / density);
                int hd = Math.round(h / density);
                hv.updateAppWidgetSize(null, wd, hd, wd, hd);
            }
        });
        return frame;
    }

    private View makeClock(final ItemInfo it) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);

        TextClock time = new TextClock(this);
        time.setFormat12Hour("h:mm");
        time.setFormat24Hour("HH:mm");
        time.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        time.setTextColor(0xFFFFFFFF);
        time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 84);
        time.setShadowLayer(6f, 0f, 2f, 0x80000000);
        time.setIncludeFontPadding(false);
        box.addView(time, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE, MMMM d");
        date.setFormat24Hour("EEEE, MMMM d");
        date.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        date.setTextColor(0xFF33B5E5);
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        date.setShadowLayer(4f, 0f, 1f, 0x80000000);
        box.addView(date, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        box.setOnLongClickListener(v -> {
            showItemMenu(it);
            return true;
        });
        return box;
    }

    // ------------------------------------------------------------------ menus

    private void showHomeMenu() {
        final String[] items = {"Widgets", "Wallpapers", "Settings"};
        new AlertDialog.Builder(this)
                .setTitle("Home screen")
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        openDrawer(false, true);
                    } else if (which == 1) {
                        startActivity(new Intent(this, WallpaperActivity.class));
                    } else {
                        startActivity(new Intent(this, SettingsActivity.class));
                    }
                })
                .show();
    }

    private void showItemMenu(final ItemInfo it) {
        final List<String> labels = new ArrayList<>();
        labels.add("Move");
        if (it.type == ItemInfo.APP) {
            labels.add("App info");
        }
        labels.add("Remove");

        new AlertDialog.Builder(this)
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    String s = labels.get(which);
                    if ("Move".equals(s)) {
                        pendingMove = it;
                        toast("Tap an empty spot");
                    } else if ("App info".equals(s)) {
                        launch(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + it.pkg)));
                    } else {
                        removeItem(it);
                    }
                })
                .show();
    }

    private void removeItem(ItemInfo it) {
        if (it.type == ItemInfo.WIDGET && it.widgetId >= 0) {
            host.deleteAppWidgetId(it.widgetId);
        }
        store.remove(it);
        refreshPage(it.page);
    }

    private void onEmptyCellTapped(int page, int x, int y) {
        if (pendingMove == null) {
            return;
        }
        ItemInfo it = pendingMove;
        if (store.fits(page, x, y, it.spanX, it.spanY, cols, rows, it)) {
            int oldPage = it.page;
            it.page = page;
            it.cellX = x;
            it.cellY = y;
            store.save();
            pendingMove = null;
            refreshPage(oldPage);
            if (oldPage != page) {
                refreshPage(page);
            }
        } else {
            toast("Doesn't fit there");
        }
    }

    // ------------------------------------------------------------------ dock

    private void setupDock() {
        dock.removeAllViews();
        dock.setBackgroundColor(prefs.dockDim() ? 0x66000000 : 0x00000000);
        findViewById(R.id.dock_line).setVisibility(prefs.dockDim() ? View.VISIBLE : View.INVISIBLE);

        addDockShortcut(new Intent(Intent.ACTION_DIAL));
        addDockShortcut(categoryIntent(Intent.CATEGORY_APP_MESSAGING));

        ImageView all = new ImageView(this);
        all.setImageResource(R.drawable.ic_apps);
        styleDockItem(all);
        all.setOnClickListener(v -> openDrawer(false, false));
        dock.addView(all, dockParams());

        addDockShortcut(categoryIntent(Intent.CATEGORY_APP_BROWSER));
        addDockShortcut(new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA));
    }

    private Intent categoryIntent(String category) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(category);
        return intent;
    }

    private void addDockShortcut(final Intent base) {
        PackageManager pm = getPackageManager();
        ResolveInfo ri = pm.resolveActivity(base, 0);
        if (ri == null || ri.activityInfo == null
                || "android".equals(ri.activityInfo.packageName)) {
            List<ResolveInfo> all = pm.queryIntentActivities(base, 0);
            ri = all.isEmpty() ? null : all.get(0);
        }
        if (ri == null) {
            dock.addView(new View(this), dockParams());
            return;
        }
        final ComponentName cn =
                new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name);
        ImageView view = new ImageView(this);
        view.setImageDrawable(ri.loadIcon(pm));
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
        int dockHeight = dp(76);
        int pad = Math.max(dp(4), (dockHeight - iconPx()) / 2);
        view.setPadding(pad, pad, pad, pad);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        TypedValue tv = new TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true)) {
            view.setBackgroundResource(tv.resourceId);
        }
    }

    // ------------------------------------------------------------------ drawer

    private void setupDrawer() {
        appAdapter = new AppAdapter(new AppAdapter.Actions() {
            @Override
            public void onClick(AppInfo a) {
                launchApp(a);
            }

            @Override
            public void onLongClick(View anchor, AppInfo a) {
                showAppMenu(a);
            }
        }, iconPx(), prefs.labelSize(), prefs.showLabels());

        appsList.setLayoutManager(new GridLayoutManager(this, prefs.drawerCols()));
        appsList.setAdapter(appAdapter);
        appsList.setHasFixedSize(true);
        appsList.setItemViewCacheSize(20);
        appsList.setItemAnimator(null);

        widgetAdapter = new WidgetAdapter(this::startAddWidget);
        widgetsList.setLayoutManager(new LinearLayoutManager(this));
        widgetsList.setAdapter(widgetAdapter);
        widgetsList.setHasFixedSize(true);

        tabApps.setOnClickListener(v -> selectTab(false));
        tabWidgets.setOnClickListener(v -> selectTab(true));

        searchField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                appAdapter.filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });
        searchField.setOnEditorActionListener((v, actionId, event) -> {
            AppInfo first = appAdapter.first();
            if (first != null) {
                launchApp(first);
            } else {
                String q = searchField.getText().toString().trim();
                if (!q.isEmpty()) {
                    Intent i = new Intent(Intent.ACTION_WEB_SEARCH);
                    i.putExtra(SearchManager.QUERY, q);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    launch(i);
                    closeDrawer();
                }
            }
            return true;
        });
    }

    private boolean isDrawerOpen() {
        return drawer.getVisibility() == View.VISIBLE;
    }

    private void selectTab(boolean widgets) {
        tabApps.setSelected(!widgets);
        tabWidgets.setSelected(widgets);
        tabApps.setTextColor(widgets ? 0x99FFFFFF : 0xFF33B5E5);
        tabWidgets.setTextColor(widgets ? 0xFF33B5E5 : 0x99FFFFFF);
        appsList.setVisibility(widgets ? View.GONE : View.VISIBLE);
        searchField.setVisibility(widgets ? View.GONE : View.VISIBLE);
        widgetsList.setVisibility(widgets ? View.VISIBLE : View.GONE);
        if (widgets && !widgetsLoaded) {
            loadWidgets();
        }
    }

    private void openDrawer(boolean focusSearch, boolean widgets) {
        pendingMove = null;
        final long t0 = SystemClock.uptimeMillis();
        drawer.animate().cancel();
        searchField.setText("");
        selectTab(widgets);
        drawer.setAlpha(0f);
        drawer.setScaleX(1.08f);
        drawer.setScaleY(1.08f);
        drawer.setVisibility(View.VISIBLE);
        drawer.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).withLayer().start();

        drawer.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                drawer.getViewTreeObserver().removeOnPreDrawListener(this);
                DebugLog.d("drawer first frame: " + (SystemClock.uptimeMillis() - t0) + " ms");
                return true;
            }
        });

        if (focusSearch && !widgets) {
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
        drawer.animate().cancel();
        drawer.animate().alpha(0f).scaleX(1.08f).scaleY(1.08f).setDuration(150)
                .withLayer()
                .withEndAction(() -> {
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

    private void launchApp(AppInfo a) {
        launch(a.launchIntent());
        closeDrawer();
    }

    private void showAppMenu(final AppInfo a) {
        final String[] items = {"Add to home screen", "App info", "Uninstall"};
        new AlertDialog.Builder(this)
                .setTitle(a.label)
                .setItems(items, (d, which) -> {
                    Uri pkg = Uri.parse("package:" + a.packageName);
                    if (which == 0) {
                        addAppToHome(a);
                    } else if (which == 1) {
                        launch(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg));
                    } else {
                        launch(new Intent(Intent.ACTION_DELETE, pkg));
                    }
                })
                .show();
    }

    private void addAppToHome(AppInfo a) {
        int[] slot = store.findFree(pager.getCurrentItem(), pageCount, cols, rows, 1, 1);
        if (slot == null) {
            toast("No room on the home screen");
            return;
        }
        ItemInfo it = new ItemInfo();
        it.type = ItemInfo.APP;
        it.pkg = a.component.getPackageName();
        it.cls = a.component.getClassName();
        it.page = slot[0];
        it.cellX = slot[1];
        it.cellY = slot[2];
        store.add(it);
        refreshPage(it.page);
        closeDrawer();
        pager.setCurrentItem(it.page, true);
    }

    // ------------------------------------------------------------------ widgets

    private void loadWidgets() {
        widgetsLoaded = true;
        final int cellW = Math.max(1, (pager.getWidth() - dp(12)) / cols);
        final int cellH = Math.max(1, (pager.getHeight() - dp(8)) / rows);
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            List<WidgetAdapter.Entry> out = new ArrayList<>();
            List<AppWidgetProviderInfo> providers = awm.getInstalledProviders();
            for (AppWidgetProviderInfo p : providers) {
                try {
                    WidgetAdapter.Entry e = new WidgetAdapter.Entry();
                    e.info = p;
                    e.label = p.loadLabel(pm);
                    int sx = clamp((int) Math.ceil(p.minWidth / (float) cellW), 1, cols);
                    int sy = clamp((int) Math.ceil(p.minHeight / (float) cellH), 1, rows);
                    e.sizeText = sx + " × " + sy;
                    Drawable d = p.loadPreviewImage(this, 0);
                    if (d == null) {
                        d = p.loadIcon(this, 0);
                    }
                    if (d != null) {
                        e.preview = IconUtil.toScaledBitmap(d, dp(320));
                    }
                    out.add(e);
                } catch (Exception ex) {
                    DebugLog.d("widget skipped: " + ex);
                }
            }
            Collections.sort(out, (a, b) -> String.valueOf(a.label)
                    .compareToIgnoreCase(String.valueOf(b.label)));
            runOnUiThread(() -> widgetAdapter.setEntries(out));
            DebugLog.d("widgets loaded: " + out.size());
        }).start();
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private void startAddWidget(AppWidgetProviderInfo info) {
        int id = host.allocateAppWidgetId();
        pendingWidgetId = id;
        boolean bound;
        try {
            bound = awm.bindAppWidgetIdIfAllowed(id, info.provider);
        } catch (Exception e) {
            bound = false;
        }
        if (bound) {
            configureOrPlace(id);
        } else {
            Intent i = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
            i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
            i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider);
            try {
                startActivityForResult(i, REQ_BIND);
            } catch (Exception e) {
                host.deleteAppWidgetId(id);
                pendingWidgetId = -1;
                toast("This widget can't be added");
            }
        }
    }

    private void configureOrPlace(int id) {
        AppWidgetProviderInfo info = awm.getAppWidgetInfo(id);
        if (info != null && info.configure != null) {
            try {
                host.startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CONFIGURE, null);
                return;
            } catch (Exception e) {
                DebugLog.d("configure failed, placing anyway: " + e);
            }
        }
        placeWidget(id);
    }

    private void placeWidget(int id) {
        AppWidgetProviderInfo info = awm.getAppWidgetInfo(id);
        if (info == null) {
            host.deleteAppWidgetId(id);
            return;
        }
        int cellW = Math.max(1, (pager.getWidth() - dp(12)) / cols);
        int cellH = Math.max(1, (pager.getHeight() - dp(8)) / rows);
        int sx = clamp((int) Math.ceil(info.minWidth / (float) cellW), 1, cols);
        int sy = clamp((int) Math.ceil(info.minHeight / (float) cellH), 1, rows);

        int[] slot = store.findFree(pager.getCurrentItem(), pageCount, cols, rows, sx, sy);
        if (slot == null) {
            toast("No room on the home screen");
            host.deleteAppWidgetId(id);
            return;
        }
        ItemInfo it = new ItemInfo();
        it.type = ItemInfo.WIDGET;
        it.widgetId = id;
        it.page = slot[0];
        it.cellX = slot[1];
        it.cellY = slot[2];
        it.spanX = sx;
        it.spanY = sy;
        store.add(it);
        refreshPage(it.page);
        closeDrawer();
        pager.setCurrentItem(it.page, true);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_BIND && requestCode != REQ_CONFIGURE) {
            return;
        }
        int id = pendingWidgetId;
        if (id < 0 && data != null) {
            id = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        }
        pendingWidgetId = -1;
        if (id < 0) {
            return;
        }
        if (resultCode != RESULT_OK) {
            host.deleteAppWidgetId(id);
            return;
        }
        if (requestCode == REQ_BIND) {
            pendingWidgetId = id;
            configureOrPlace(id);
        } else {
            placeWidget(id);
        }
    }
}
