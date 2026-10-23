package com.holo.launcher;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;

/**
 * Holo lock screen shown over the system lock screen. If the phone has a PIN,
 * pattern or password, the system asks for it right after the ring is dragged.
 */
public class LockActivity extends Activity {

    private LockRingView ring;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        float d = getResources().getDisplayMetrics().density;

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0x66000000);
        root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(0, (int) (72 * d), 0, (int) (24 * d));

        TextClock time = new TextClock(this);
        time.setFormat12Hour("h:mm");
        time.setFormat24Hour("HH:mm");
        time.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        time.setTextColor(0xFFFFFFFF);
        time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 88);
        time.setIncludeFontPadding(false);
        col.addView(time, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE, MMMM d");
        date.setFormat24Hour("EEEE, MMMM d");
        date.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        date.setTextColor(0xFF33B5E5);
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        col.addView(date, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View spacer = new View(this);
        col.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));

        ring = new LockRingView(this);
        ring.setListener(this::unlock);
        col.addView(ring, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (300 * d)));

        ImageView camera = new ImageView(this);
        camera.setImageResource(android.R.drawable.ic_menu_camera);
        camera.setPadding((int) (16 * d), (int) (16 * d), (int) (16 * d), (int) (16 * d));
        camera.setOnClickListener(v -> openCamera());
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                (int) (72 * d), (int) (72 * d));
        cp.gravity = Gravity.END;
        cp.rightMargin = (int) (24 * d);
        col.addView(camera, cp);

        root.addView(col, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
    }

    private void openCamera() {
        try {
            startActivity(new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            DebugLog.d("lock camera failed: " + e);
        }
    }

    private void unlock() {
        if (Build.VERSION.SDK_INT >= 26) {
            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (km != null) {
                km.requestDismissKeyguard(this, new KeyguardManager.KeyguardDismissCallback() {
                    @Override
                    public void onDismissSucceeded() {
                        finish();
                    }

                    @Override
                    public void onDismissCancelled() {
                        ring.reset();
                    }

                    @Override
                    public void onDismissError() {
                        finish();
                    }
                });
                return;
            }
        }
        finish();
    }

    @Override
    public void onBackPressed() {
        // The lock screen can't be dismissed with Back.
    }
}
