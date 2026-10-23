package com.holo.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** The thin segmented line under the workspace, with a sliding blue highlight. */
public class HoloPageIndicator extends View {

    private final Paint off = new Paint();
    private final Paint on = new Paint();
    private final float barHeight;
    private final float gap;
    private int count = 1;
    private float position = 0f;

    public HoloPageIndicator(Context context) {
        super(context);
        float d = context.getResources().getDisplayMetrics().density;
        barHeight = 3 * d;
        gap = 4 * d;
        off.setColor(0x44FFFFFF);
        on.setColor(0xFF33B5E5);
    }

    public void setCount(int count) {
        this.count = Math.max(1, count);
        invalidate();
    }

    public void setPosition(float position) {
        this.position = position;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float top = (getHeight() - barHeight) / 2f;
        float seg = (w - gap * (count - 1)) / count;
        for (int i = 0; i < count; i++) {
            float left = i * (seg + gap);
            canvas.drawRect(left, top, left + seg, top + barHeight, off);
        }
        float hl = position * (seg + gap);
        canvas.drawRect(hl, top, hl + seg, top + barHeight, on);
    }
}
