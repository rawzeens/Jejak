package com.r4xn.jejak.rec;

import android.os.Handler;
import android.os.Looper;

import com.r4xn.jejak.core.Fix;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** What the recorder is doing right now, for the screens to show. */
public final class Live {
    public interface Listener {
        void changed();
    }

    public static final int KEEP = 20;

    /** Is the recorder running? */
    public static volatile boolean running;
    /** The newest reading, or null. */
    public static volatile Fix last;
    /** "GPS" or "Wi-Fi / mobile network". */
    public static volatile String source = "";
    /** Satellites used for the position, and seen. */
    public static volatile int satsUsed, satsSeen;
    /** Readings saved since midnight. */
    public static volatile long today;
    /** Why recording isn't working, or null. */
    public static volatile String problem;

    private static final ArrayDeque<Fix> recent = new ArrayDeque<>();
    private static final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final Runnable tell = () -> {
        for (Listener l : listeners) l.changed();
    };

    private Live() {
    }

    public static void listen(Listener l) {
        listeners.add(l);
    }

    public static void stopListening(Listener l) {
        listeners.remove(l);
    }

    /** Called from the recorder's thread for each reading saved. */
    static void reading(Fix f, String from) {
        last = f;
        source = from;
        problem = null;
        today++;
        synchronized (recent) {
            recent.addFirst(f);
            while (recent.size() > KEEP) recent.removeLast();
        }
        changed();
    }

    /** The newest readings, newest first. */
    public static List<Fix> recent() {
        synchronized (recent) {
            return new ArrayList<>(recent);
        }
    }

    static void seed(List<Fix> newestFirst, long countToday) {
        synchronized (recent) {
            recent.clear();
            recent.addAll(newestFirst);
        }
        if (!newestFirst.isEmpty() && last == null) last = newestFirst.get(0);
        today = countToday;
        changed();
    }

    static void changed() {
        main.removeCallbacks(tell);
        main.post(tell);
    }
}
