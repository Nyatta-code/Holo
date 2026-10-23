package com.holo.launcher;

import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;

import java.util.Locale;

/** One launchable app. The icon is pre-rendered to a bitmap for smooth scrolling. */
public class AppInfo {

    public final CharSequence label;
    public final String labelLower;
    public final String packageName;
    public final ComponentName component;
    public final Bitmap icon;
    public final long id;

    public AppInfo(CharSequence label, ComponentName component, Bitmap icon) {
        this.label = label;
        this.labelLower = label.toString().toLowerCase(Locale.getDefault());
        this.component = component;
        this.packageName = component.getPackageName();
        this.icon = icon;
        this.id = component.flattenToString().hashCode();
    }

    public Intent launchIntent() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setComponent(component);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return intent;
    }
}
