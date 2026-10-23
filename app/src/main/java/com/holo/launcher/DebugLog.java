package com.holo.launcher;

import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;

/** Small in-memory ring buffer shown in the Debug menu. */
public final class DebugLog {

    private static final int MAX = 300;
    private static final ArrayDeque<String> LINES = new ArrayDeque<>();
    private static final SimpleDateFormat FMT = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    private DebugLog() { }

    public static synchronized void d(String msg) {
        Log.d("HoloLauncher", msg);
        LINES.addLast(FMT.format(new Date()) + "  " + msg);
        while (LINES.size() > MAX) {
            LINES.removeFirst();
        }
    }

    public static synchronized String dump() {
        StringBuilder sb = new StringBuilder();
        for (String s : LINES) {
            sb.append(s).append('\n');
        }
        return sb.toString();
    }

    public static synchronized void clear() {
        LINES.clear();
    }
}
