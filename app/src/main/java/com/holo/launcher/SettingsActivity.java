package com.holo.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.provider.Settings;
import android.widget.Toast;

import java.util.List;

/** Holo-style settings screen (framework PreferenceFragment keeps the original look). */
public class SettingsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();
    }

    public static class SettingsFragment extends PreferenceFragment {

        private int versionTaps = 0;
        private PreferenceCategory devCategory;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);

            final LauncherApp app = LauncherApp.get(getActivity());

            devCategory = (PreferenceCategory) findPreference("dev_cat");

            findPreference("wallpapers").setOnPreferenceClickListener(p -> {
                startActivity(new Intent(getActivity(), WallpaperActivity.class));
                return true;
            });

            findPreference("lock_enabled").setOnPreferenceChangeListener((p, newValue) -> {
                boolean enable = (Boolean) newValue;
                if (enable) {
                    if (!LauncherApp.canOverlay(getActivity())) {
                        Toast.makeText(getActivity(),
                                "Allow \"Display over other apps\", then turn this on again",
                                Toast.LENGTH_LONG).show();
                        startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getActivity().getPackageName())));
                        return false;
                    }
                    LockService.start(getActivity());
                } else {
                    getActivity().stopService(new Intent(getActivity(), LockService.class));
                }
                return true;
            });

            findPreference("reset_layout").setOnPreferenceClickListener(p -> {
                new AlertDialog.Builder(getActivity())
                        .setTitle("Reset home screen?")
                        .setMessage("Removes every shortcut and widget from the home screen.")
                        .setPositiveButton("Reset", (d, w) -> {
                            resetLayout(app);
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                return true;
            });

            findPreference("version").setOnPreferenceClickListener(p -> {
                if (app.prefs.debugEnabled()) {
                    Toast.makeText(getActivity(), "Debug menu is already on",
                            Toast.LENGTH_SHORT).show();
                    return true;
                }
                versionTaps++;
                if (versionTaps >= 7) {
                    app.prefs.setBool("debug_enabled", true);
                    getPreferenceScreen().addPreference(devCategory);
                    Toast.makeText(getActivity(), "Debug menu unlocked", Toast.LENGTH_SHORT).show();
                } else if (versionTaps >= 3) {
                    Toast.makeText(getActivity(), (7 - versionTaps) + " more taps...",
                            Toast.LENGTH_SHORT).show();
                }
                return true;
            });

            findPreference("debug_menu").setOnPreferenceClickListener(p -> {
                startActivity(new Intent(getActivity(), DebugActivity.class));
                return true;
            });

            // Hidden until "Build number" is tapped 7 times. Must come last: once the
            // category is removed, findPreference can no longer see its children.
            if (!app.prefs.debugEnabled()) {
                getPreferenceScreen().removePreference(devCategory);
            }
        }

        private void resetLayout(LauncherApp app) {
            android.appwidget.AppWidgetHost host =
                    new android.appwidget.AppWidgetHost(getActivity(), 1024);
            List<Integer> ids = app.store.widgetIds();
            for (int id : ids) {
                host.deleteAppWidgetId(id);
            }
            app.store.reset();
            // Touch a preference so the home screen rebuilds when we go back to it.
            app.prefs.setBool("layout_reset_flag", !app.prefs.raw().getBoolean("layout_reset_flag", false));
            Toast.makeText(getActivity(), "Home screen reset", Toast.LENGTH_SHORT).show();
        }
    }
}
