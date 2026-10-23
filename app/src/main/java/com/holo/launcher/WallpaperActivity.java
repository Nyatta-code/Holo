package com.holo.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Wallpaper picker with the Holo-style wallpapers. */
public class WallpaperActivity extends Activity {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private Bitmap[] thumbs;
    private BaseAdapter adapter;
    private int screenW;
    private int screenH;
    private int thumbW;
    private int thumbH;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        screenW = dm.widthPixels;
        screenH = dm.heightPixels;
        thumbW = screenW / 2 - (int) (16 * dm.density);
        thumbH = thumbW * screenH / screenW;
        thumbs = new Bitmap[HoloWallpapers.NAMES.length];

        GridView grid = new GridView(this);
        grid.setNumColumns(2);
        int pad = (int) (8 * dm.density);
        grid.setPadding(pad, pad, pad, pad);
        grid.setHorizontalSpacing(pad);
        grid.setVerticalSpacing(pad);
        grid.setClipToPadding(false);

        adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return HoloWallpapers.NAMES.length;
            }

            @Override
            public Object getItem(int position) {
                return position;
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                ImageView iv = (ImageView) convertView;
                if (iv == null) {
                    iv = new ImageView(WallpaperActivity.this);
                    iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    iv.setLayoutParams(new GridView.LayoutParams(thumbW, thumbH));
                    iv.setBackgroundColor(0xFF111111);
                }
                iv.setImageBitmap(thumbs[position]);
                return iv;
            }
        };
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, position, id) -> preview(position));
        setContentView(grid);

        // Render thumbnails one by one in the background.
        for (int i = 0; i < thumbs.length; i++) {
            final int idx = i;
            executor.execute(() -> {
                Bitmap b = HoloWallpapers.render(thumbW, thumbH, idx);
                main.post(() -> {
                    thumbs[idx] = b;
                    adapter.notifyDataSetChanged();
                });
            });
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "Other wallpapers…");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 1) {
            try {
                startActivity(Intent.createChooser(
                        new Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper"));
            } catch (Exception e) {
                Toast.makeText(this, "No wallpaper picker found", Toast.LENGTH_SHORT).show();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void preview(final int idx) {
        if (thumbs[idx] == null) {
            return;
        }
        ImageView iv = new ImageView(this);
        iv.setImageBitmap(thumbs[idx]);
        iv.setAdjustViewBounds(true);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        new AlertDialog.Builder(this)
                .setTitle(HoloWallpapers.NAMES[idx])
                .setView(iv)
                .setPositiveButton("Set wallpaper", (d, w) -> applyWallpaper(idx))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void applyWallpaper(final int idx) {
        final Context ctx = getApplicationContext();
        Toast.makeText(this, "Setting wallpaper…", Toast.LENGTH_SHORT).show();
        executor.execute(() -> {
            String msg;
            try {
                // Twice the screen width so the wallpaper can scroll with the home pages.
                Bitmap full = HoloWallpapers.render(screenW * 2, screenH, idx);
                WallpaperManager wm = WallpaperManager.getInstance(ctx);
                if (Build.VERSION.SDK_INT >= 24) {
                    wm.setBitmap(full, null, true, WallpaperManager.FLAG_SYSTEM);
                } else {
                    wm.setBitmap(full);
                }
                full.recycle();
                msg = "Wallpaper set";
            } catch (Exception e) {
                msg = "Couldn't set wallpaper";
                DebugLog.d("setWallpaper failed: " + e);
            }
            final String shown = msg;
            main.post(() -> Toast.makeText(ctx, shown, Toast.LENGTH_SHORT).show());
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
