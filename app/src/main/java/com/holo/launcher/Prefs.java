package com.holo.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

/** Typed access to the launcher settings (stored in the default shared preferences). */
public final class Prefs {

    private final SharedPreferences sp;

    public Prefs(Context c) {
        sp = PreferenceManager.getDefaultSharedPreferences(c);
    }

    public SharedPreferences raw() {
        return sp;
    }

    private int num(String key, int def) {
        try {
            return Integer.parseInt(sp.getString(key, String.valueOf(def)));
        } catch (Exception e) {
            return def;
        }
    }

    public int cols() { return num("cols", 4); }
    public int rows() { return num("rows", 4); }
    public int pages() { return Math.max(1, num("pages", 3)); }
    public int iconScale() { return num("icon_scale", 100); }
    public int labelSize() { return num("label_size", 12); }
    public int drawerCols() { return num("drawer_cols", 4); }

    public boolean showLabels() { return sp.getBoolean("show_labels", true); }
    public boolean showSearch() { return sp.getBoolean("show_search", true); }
    public boolean showIndicator() { return sp.getBoolean("show_indicator", true); }
    public boolean dockDim() { return sp.getBoolean("dock_dim", true); }
    public boolean wallpaperScroll() { return sp.getBoolean("wallpaper_scroll", true); }
    public boolean lockEnabled() { return sp.getBoolean("lock_enabled", false); }
    public boolean debugEnabled() { return sp.getBoolean("debug_enabled", false); }
    public boolean debugFps() { return sp.getBoolean("debug_fps", false); }

    public String transition() { return sp.getString("transition", "standard"); }

    /** Index of the "home" page, always inside the page range. */
    public int homePage() {
        int pages = pages();
        return Math.min(pages - 1, pages / 2);
    }

    public void setBool(String key, boolean value) {
        sp.edit().putBoolean(key, value).apply();
    }
}
