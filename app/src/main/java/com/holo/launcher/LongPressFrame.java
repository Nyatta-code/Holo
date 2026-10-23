package com.holo.launcher;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

/** Wraps a widget so a long press can be detected even though the widget eats touches. */
public class LongPressFrame extends FrameLayout {

    public interface OnLongPress {
        void onLongPress();
    }

    private OnLongPress callback;
    private float downX;
    private float downY;
    private boolean fired;
    private final int slop;

    private final Runnable trigger = new Runnable() {
        @Override
        public void run() {
            fired = true;
            if (callback != null) {
                callback.onLongPress();
            }
        }
    };

    public LongPressFrame(Context context) {
        super(context);
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public void setOnLongPress(OnLongPress cb) {
        callback = cb;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                fired = false;
                downX = ev.getX();
                downY = ev.getY();
                postDelayed(trigger, ViewConfiguration.getLongPressTimeout());
                break;
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(ev.getX() - downX) > slop || Math.abs(ev.getY() - downY) > slop) {
                    removeCallbacks(trigger);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(trigger);
                break;
            default:
                break;
        }
        return fired;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (fired) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                fired = false;
            }
            return true;
        }
        return super.onTouchEvent(event);
    }
}
