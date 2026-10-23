package com.holo.launcher;

import android.content.ComponentName;
import android.content.Intent;
import android.graphics.drawable.Drawable;

/** One launchable app shown in the drawer. */
public class AppInfo {

    public final CharSequence label;
    public final String packageName;
    public final ComponentName component;
    public final Drawable icon;

    public AppInfo(CharSequence label, ComponentName component, Drawable icon) {
        this.label = label;
        this.component = component;
        this.packageName = component.getPackageName();
        this.icon = icon;
    }

    public Intent launchIntent() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setComponent(component);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return intent;
    }
}
