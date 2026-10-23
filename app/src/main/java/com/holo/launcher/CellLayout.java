package com.holo.launcher;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

/** A fixed grid page. Children are placed by cell position and span. */
public class CellLayout extends ViewGroup {

    public interface Listener {
        void onEmptyLongPress();

        void onEmptyTap(int cellX, int cellY);
    }

    public static class LP extends ViewGroup.LayoutParams {
        public final ItemInfo item;

        public LP(ItemInfo item) {
            super(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            this.item = item;
        }
    }

    private int cols = 4;
    private int rows = 4;
    private final int margin;
    private Listener listener;
    private final GestureDetector detector;

    public CellLayout(Context context) {
        super(context);
        margin = (int) (2 * context.getResources().getDisplayMetrics().density + 0.5f);
        detector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                if (listener != null) {
                    int[] c = cellAt(e.getX(), e.getY());
                    listener.onEmptyTap(c[0], c[1]);
                }
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                if (listener != null) {
                    listener.onEmptyLongPress();
                }
            }
        });
    }

    public void setGrid(int cols, int rows) {
        this.cols = Math.max(1, cols);
        this.rows = Math.max(1, rows);
        requestLayout();
    }

    public void setListener(Listener l) {
        listener = l;
    }

    private int[] cellAt(float x, float y) {
        int cw = Math.max(1, (getWidth() - getPaddingLeft() - getPaddingRight()) / cols);
        int ch = Math.max(1, (getHeight() - getPaddingTop() - getPaddingBottom()) / rows);
        int cx = (int) ((x - getPaddingLeft()) / cw);
        int cy = (int) ((y - getPaddingTop()) / ch);
        cx = Math.max(0, Math.min(cols - 1, cx));
        cy = Math.max(0, Math.min(rows - 1, cy));
        return new int[]{cx, cy};
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return detector.onTouchEvent(event);
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof LP;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(w, h);

        int cw = (w - getPaddingLeft() - getPaddingRight()) / cols;
        int ch = (h - getPaddingTop() - getPaddingBottom()) / rows;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            LP lp = (LP) child.getLayoutParams();
            int cwid = Math.max(1, lp.item.spanX * cw - 2 * margin);
            int chei = Math.max(1, lp.item.spanY * ch - 2 * margin);
            child.measure(MeasureSpec.makeMeasureSpec(cwid, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(chei, MeasureSpec.EXACTLY));
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int cw = (getWidth() - getPaddingLeft() - getPaddingRight()) / cols;
        int ch = (getHeight() - getPaddingTop() - getPaddingBottom()) / rows;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            LP lp = (LP) child.getLayoutParams();
            int left = getPaddingLeft() + lp.item.cellX * cw + margin;
            int top = getPaddingTop() + lp.item.cellY * ch + margin;
            child.layout(left, top, left + child.getMeasuredWidth(), top + child.getMeasuredHeight());
        }
    }
}
