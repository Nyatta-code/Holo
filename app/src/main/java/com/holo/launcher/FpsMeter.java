package com.holo.launcher;

import android.view.Choreographer;

/** Counts frames with Choreographer. Call start()/stop() from the main thread only. */
public final class FpsMeter implements Choreographer.FrameCallback {

    private static final FpsMeter INSTANCE = new FpsMeter();

    public static FpsMeter get() {
        return INSTANCE;
    }

    private int users;
    private boolean running;
    private long lastFrameNs;
    private long windowStartNs;
    private int frames;
    private int slow;

    private volatile float fps;
    private volatile int jankPercent;

    private FpsMeter() { }

    public void start() {
        users++;
        if (users == 1) {
            running = true;
            lastFrameNs = 0;
            windowStartNs = 0;
            frames = 0;
            slow = 0;
            Choreographer.getInstance().postFrameCallback(this);
        }
    }

    public void stop() {
        if (users > 0) {
            users--;
            if (users == 0) {
                running = false;
                Choreographer.getInstance().removeFrameCallback(this);
            }
        }
    }

    public float getFps() {
        return fps;
    }

    public int getJankPercent() {
        return jankPercent;
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        if (!running) {
            return;
        }
        if (lastFrameNs != 0) {
            frames++;
            if (frameTimeNanos - lastFrameNs > 20_000_000L) {
                slow++;
            }
        } else {
            windowStartNs = frameTimeNanos;
        }
        lastFrameNs = frameTimeNanos;

        long elapsed = frameTimeNanos - windowStartNs;
        if (windowStartNs != 0 && elapsed >= 1_000_000_000L) {
            fps = frames * 1_000_000_000f / elapsed;
            jankPercent = frames == 0 ? 0 : slow * 100 / frames;
            frames = 0;
            slow = 0;
            windowStartNs = frameTimeNanos;
        }
        Choreographer.getInstance().postFrameCallback(this);
    }
}
