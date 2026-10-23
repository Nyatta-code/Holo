package com.holo.launcher;

import android.content.ComponentName;

/** One thing placed on the workspace: an app shortcut, a widget or the clock. */
public class ItemInfo {

    public static final int APP = 0;
    public static final int WIDGET = 1;
    public static final int CLOCK = 2;

    public int id;
    public int type;
    public int page;
    public int cellX;
    public int cellY;
    public int spanX = 1;
    public int spanY = 1;
    public String pkg;
    public String cls;
    public int widgetId = -1;

    public ComponentName component() {
        return (pkg == null || cls == null) ? null : new ComponentName(pkg, cls);
    }
}
