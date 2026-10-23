package com.holo.launcher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** The Holo unlock ring: drag the lock handle out through the ring to unlock. */
public class LockRingView extends View {

    public interface Listener {
        void onUnlock();
    }

    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handleFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handleStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glyph = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;

    private float cx;
    private float cy;
    private float ringR;
    private float handleR;
    private float handleX;
    private float handleY;
    private boolean dragging;
    private boolean done;
    private Listener listener;

    public LockRingView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;

        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(3 * density);
        ringPaint.setColor(0xAA33B5E5);

        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(12 * density);
        glowPaint.setColor(0x2233B5E5);

        handleFill.setStyle(Paint.Style.FILL);
        handleFill.setColor(0xCC000000);

        handleStroke.setStyle(Paint.Style.STROKE);
        handleStroke.setStrokeWidth(3 * density);
        handleStroke.setColor(0xFF33B5E5);

        glyph.setStyle(Paint.Style.FILL);
        glyph.setColor(0xFFFFFFFF);
    }

    public void setListener(Listener l) {
        listener = l;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        cx = w / 2f;
        cy = h / 2f;
        ringR = Math.min(w, h) * 0.42f;
        handleR = ringR * 0.27f;
        handleX = cx;
        handleY = cy;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawCircle(cx, cy, ringR, glowPaint);
        canvas.drawCircle(cx, cy, ringR, ringPaint);

        canvas.drawCircle(handleX, handleY, handleR, handleFill);
        canvas.drawCircle(handleX, handleY, handleR, handleStroke);
        drawLock(canvas, handleX, handleY, handleR * 0.9f);
    }

    private void drawLock(Canvas canvas, float x, float y, float size) {
        // body
        float bw = size * 0.9f;
        float bh = size * 0.7f;
        rect.set(x - bw / 2f, y - bh * 0.1f, x + bw / 2f, y - bh * 0.1f + bh);
        canvas.drawRoundRect(rect, size * 0.1f, size * 0.1f, glyph);
        // shackle
        Paint s = new Paint(Paint.ANTI_ALIAS_FLAG);
        s.setStyle(Paint.Style.STROKE);
        s.setStrokeWidth(size * 0.16f);
        s.setColor(0xFFFFFFFF);
        float sr = bw * 0.30f;
        rect.set(x - sr, y - bh * 0.1f - sr * 1.6f, x + sr, y - bh * 0.1f + sr * 0.4f);
        canvas.drawArc(rect, 180f, 180f, false, s);
        canvas.drawLine(x - sr, y - bh * 0.1f - sr * 0.6f, x - sr, y - bh * 0.1f, s);
        canvas.drawLine(x + sr, y - bh * 0.1f - sr * 0.6f, x + sr, y - bh * 0.1f, s);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (done) {
            return true;
        }
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                float dx = e.getX() - handleX;
                float dy = e.getY() - handleY;
                dragging = Math.hypot(dx, dy) <= handleR * 1.7f;
                return dragging;
            }
            case MotionEvent.ACTION_MOVE:
                if (dragging) {
                    handleX = e.getX();
                    handleY = e.getY();
                    invalidate();
                    float dist = (float) Math.hypot(handleX - cx, handleY - cy);
                    if (dist >= ringR) {
                        done = true;
                        dragging = false;
                        if (listener != null) {
                            listener.onUnlock();
                        }
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    dragging = false;
                    snapBack();
                }
                return true;
            default:
                return true;
        }
    }

    /** Lets the lock screen use the ring again after an unlock that was cancelled. */
    public void reset() {
        done = false;
        dragging = false;
        snapBack();
    }

    private void snapBack() {
        final float fromX = handleX;
        final float fromY = handleY;
        ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(180);
        va.addUpdateListener(a -> {
            float t = (Float) a.getAnimatedValue();
            handleX = fromX + (cx - fromX) * t;
            handleY = fromY + (cy - fromY) * t;
            invalidate();
        });
        va.start();
    }
}
