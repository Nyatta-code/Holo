package com.holo.launcher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import java.util.Random;

/**
 * Holo-style wallpapers drawn in code: a dark background, a soft glow and
 * flowing light ribbons in the Holo accent colours.
 */
public final class HoloWallpapers {

    public static final String[] NAMES = {
            "Holo Blue", "Holo Purple", "Holo Green", "Holo Orange",
            "Holo Red", "Holo Deep", "Holo Cyan", "Holo Steel"
    };

    private static final int[] ACCENT = {
            0xFF33B5E5, 0xFFAA66CC, 0xFF99CC00, 0xFFFFBB33,
            0xFFFF4444, 0xFF0099CC, 0xFF00DDFF, 0xFF6F8FAF
    };

    private HoloWallpapers() { }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static int scale(int color, float f) {
        return Color.rgb((int) (Color.red(color) * f), (int) (Color.green(color) * f),
                (int) (Color.blue(color) * f));
    }

    /** Draws wallpaper number idx. For scrolling wallpapers pass w = 2 x screen width. */
    public static Bitmap render(int w, int h, int idx) {
        idx = ((idx % NAMES.length) + NAMES.length) % NAMES.length;
        int accent = ACCENT[idx];
        Random rnd = new Random(1000L + idx * 77L);

        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Background: black at the top fading into a deep tint of the accent.
        p.setShader(new LinearGradient(0, 0, 0, h, 0xFF000000, scale(accent, 0.22f),
                Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);

        // Soft glow low in the picture.
        p.setShader(new RadialGradient(w * 0.5f, h * 0.95f, Math.max(w, h) * 0.6f,
                withAlpha(accent, 110), withAlpha(accent, 0), Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);

        // Light ribbons.
        for (int i = 0; i < 6; i++) {
            float y0 = h * (0.30f + rnd.nextFloat() * 0.55f);
            float y1 = h * (0.15f + rnd.nextFloat() * 0.6f);
            float bend1 = h * (rnd.nextFloat() * 0.5f - 0.15f);
            float bend2 = h * (rnd.nextFloat() * 0.5f - 0.35f);
            float thick = h * (0.02f + rnd.nextFloat() * 0.07f);

            Path top = new Path();
            top.moveTo(-50, y0);
            top.cubicTo(w * 0.30f, y0 + bend1, w * 0.65f, y0 + bend2, w + 50, y1);

            Path fill = new Path(top);
            fill.lineTo(w + 50, y1 + thick);
            fill.cubicTo(w * 0.65f, y0 + bend2 + thick, w * 0.30f, y0 + bend1 + thick,
                    -50, y0 + thick);
            fill.close();

            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(0, 0, w, 0,
                    new int[]{withAlpha(accent, 0), withAlpha(accent, 70 + rnd.nextInt(50)),
                            withAlpha(accent, 0)},
                    null, Shader.TileMode.CLAMP));
            c.drawPath(fill, p);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, h * 0.0012f));
            p.setShader(new LinearGradient(0, 0, w, 0,
                    new int[]{withAlpha(accent, 0), withAlpha(0xFFFFFFFF, 150),
                            withAlpha(accent, 0)},
                    null, Shader.TileMode.CLAMP));
            c.drawPath(top, p);
        }

        // Dust.
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 90; i++) {
            p.setColor(withAlpha(0xFFFFFFFF, 20 + rnd.nextInt(60)));
            c.drawCircle(rnd.nextFloat() * w, rnd.nextFloat() * h,
                    Math.max(1f, h * 0.0008f) * (1 + rnd.nextInt(3)), p);
        }
        return bmp;
    }
}
