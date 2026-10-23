package com.holo.launcher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

public final class IconUtil {

    private IconUtil() { }

    /** Renders any drawable (including adaptive icons) into a square bitmap. */
    public static Bitmap toBitmap(Drawable d, int size) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        d.setBounds(0, 0, size, size);
        d.draw(c);
        return b;
    }

    /** Renders a drawable scaled down so that its width is at most maxWidth. */
    public static Bitmap toScaledBitmap(Drawable d, int maxWidth) {
        int w = d.getIntrinsicWidth();
        int h = d.getIntrinsicHeight();
        if (w <= 0 || h <= 0) {
            w = maxWidth;
            h = maxWidth;
        }
        if (w > maxWidth) {
            h = Math.max(1, h * maxWidth / w);
            w = maxWidth;
        }
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        d.setBounds(0, 0, w, h);
        d.draw(c);
        return b;
    }
}
