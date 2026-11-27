package com.r4xn.jejak;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.r4xn.jejak.core.Fix;
import com.r4xn.jejak.core.Place;
import com.r4xn.jejak.core.Sample;
import com.r4xn.jejak.core.Timeline;
import com.r4xn.jejak.rec.FixDb;
import com.r4xn.jejak.rec.Live;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Settings, named places and the readings (real ones from the database, or the sample days). */
public final class Store {
    public static final int[] INTERVALS = {1, 5, 10, 30, 60};
    public static final int[] ACCURACIES = {25, 50, 65, 100, 200};
    public static final int[] STOPS = {3, 5, 10, 15};
    public static final int[] KEEP = {0, 365, 90, 30, 7};

    private final Context context;
    private final SharedPreferences prefs;
    private final FixDb db;
    private final ExecutorService work = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private List<Place> places;
    /** For screenshots: the sample's clock reads this time of day. */
    private LocalTime sampleTime;
    private final Map<String, Loaded> cache = new LinkedHashMap<>();
    private volatile long changes;

    /** A day's readings, and the same day read as a timeline. */
    public static final class Loaded {
        public final LocalDate date;
        public final List<Fix> raw;
        public final Timeline.Day day;
        final long made;
        final long version;

        Loaded(LocalDate date, List<Fix> raw, Timeline.Day day, long version) {
            this.date = date;
            this.raw = raw;
            this.day = day;
            this.version = version;
            this.made = System.currentTimeMillis();
        }
    }

    Store(Context c) {
        context = c.getApplicationContext();
        prefs = context.getSharedPreferences("jejak", Context.MODE_PRIVATE);
        db = new FixDb(context);
    }

    public FixDb db() {
        return db;
    }

    // ------------------------------------------------------------------ settings

    public boolean recording() {
        return prefs.getBoolean("recording", false);
    }

    public void setRecording(boolean on) {
        prefs.edit().putBoolean("recording", on).apply();
    }

    /** Seconds between readings. */
    public int interval() {
        return prefs.getInt("interval", 1);
    }

    public void setInterval(int s) {
        prefs.edit().putInt("interval", s).apply();
    }

    /** Use Wi-Fi and mobile network positions when there's no GPS (indoors). */
    public boolean indoor() {
        return prefs.getBoolean("indoor", true);
    }

    public void setIndoor(boolean on) {
        prefs.edit().putBoolean("indoor", on).apply();
    }

    /** Start recording again after the phone restarts. */
    public boolean restart() {
        return prefs.getBoolean("restart", true);
    }

    public void setRestart(boolean on) {
        prefs.edit().putBoolean("restart", on).apply();
    }

    public int maxAccuracy() {
        return prefs.getInt("maxAccuracy", 65);
    }

    public void setMaxAccuracy(int m) {
        prefs.edit().putInt("maxAccuracy", m).apply();
        changed();
    }

    public int minStay() {
        return prefs.getInt("minStay", 5);
    }

    public void setMinStay(int minutes) {
        prefs.edit().putInt("minStay", minutes).apply();
        changed();
    }

    /** Days to keep readings for (0 = keep them all). */
    public int keepDays() {
        return prefs.getInt("keepDays", 0);
    }

    public void setKeepDays(int days) {
        prefs.edit().putInt("keepDays", days).apply();
        work.execute(this::cleanUp);
    }

    public boolean sample() {
        return prefs.getBoolean("sample", false);
    }

    public void setSample(boolean on) {
        prefs.edit().putBoolean("sample", on).apply();
        places = null;
        changed();
    }

    public void setSampleTime(LocalTime t) {
        sampleTime = t;
        changed();
    }

    public Timeline.Rules rules() {
        Timeline.Rules r = new Timeline.Rules();
        r.maxAccuracy = maxAccuracy();
        r.minStay = minStay() * 60_000L;
        return r;
    }

    // ------------------------------------------------------------------ time

    public ZoneId zone() {
        return sample() ? Sample.ZONE : ZoneId.systemDefault();
    }

    public long now() {
        if (sample() && sampleTime != null) {
            LocalDate d = LocalDate.now(Sample.ZONE);
            return d.atTime(sampleTime).atZone(Sample.ZONE).toInstant().toEpochMilli();
        }
        return System.currentTimeMillis();
    }

    public LocalDate today() {
        return java.time.Instant.ofEpochMilli(now()).atZone(zone()).toLocalDate();
    }

    public long dayStart(LocalDate d) {
        return d.atStartOfDay(zone()).toInstant().toEpochMilli();
    }

    // ------------------------------------------------------------------ places

    public List<Place> places() {
        if (places == null) places = sample() ? Sample.places() : Place.fromJson(prefs.getString("places", "[]"));
        return places;
    }

    public void savePlaces() {
        if (!sample()) prefs.edit().putString("places", Place.toJson(places())).apply();
        changed();
    }

    /** A looked-up street address near a point, or null. */
    public String address(double lat, double lon) {
        return prefs.getString("addr:" + key(lat, lon), null);
    }

    public void setAddress(double lat, double lon, String address) {
        prefs.edit().putString("addr:" + key(lat, lon), address).apply();
    }

    private static String key(double lat, double lon) {
        return Math.round(lat * 2000) + "," + Math.round(lon * 2000);
    }

    // ------------------------------------------------------------------ readings

    /** The day's readings, oldest first. Slow for a full day: call off the main thread. */
    public List<Fix> readings(LocalDate d) {
        if (sample()) {
            if (d.equals(today())) return sampleToday();
            return Sample.has(d, today()) ? Sample.day(d, now()) : new ArrayList<>();
        }
        return db.range(dayStart(d), dayStart(d.plusDays(1)));
    }

    /** Reads a day in the background, then hands it over on the main thread. */
    public void load(LocalDate d, Consumer<Loaded> done) {
        Loaded hit = cached(d);
        if (hit != null) {
            done.accept(hit);
            return;
        }
        long v = changes;
        work.execute(() -> {
            // the same day may have been asked for twice while the first was being read
            Loaded again = cached(d);
            if (again != null) {
                main.post(() -> done.accept(again));
                return;
            }
            List<Fix> raw = readings(d);
            Loaded l = new Loaded(d, Collections.unmodifiableList(raw), Timeline.build(raw, rules()), v);
            synchronized (cache) {
                cache.put(d.toString(), l);
                while (cache.size() > 2) cache.remove(cache.keySet().iterator().next());
            }
            main.post(() -> done.accept(l));
        });
    }

    /** A day already read, if it's still up to date. Today is read again after half a minute while recording. */
    public Loaded cached(LocalDate d) {
        synchronized (cache) {
            Loaded l = cache.get(d.toString());
            if (l == null || l.version != changes) return null;
            if (!sample() && d.equals(today()) && System.currentTimeMillis() - l.made > 30_000) return null;
            return l;
        }
    }

    /** Something changed that alters how days read. */
    public void changed() {
        changes++;
    }


    private List<Fix> sampleToday;
    private long sampleMinute = -1;

    /** Today's sample readings so far, made again once a minute. */
    private synchronized List<Fix> sampleToday() {
        long minute = now() / 60_000;
        if (sampleToday == null || minute != sampleMinute) {
            sampleToday = Collections.unmodifiableList(Sample.day(today(), now()));
            sampleMinute = minute;
        }
        return sampleToday;
    }

    /** The newest reading, or null. */
    public Fix latest() {
        if (sample()) {
            List<Fix> t = sampleToday();
            return t.isEmpty() ? null : t.get(t.size() - 1);
        }
        return Live.last;
    }

    /** The newest readings, newest first. */
    public List<Fix> recent() {
        if (sample()) {
            List<Fix> t = sampleToday();
            List<Fix> out = new ArrayList<>();
            for (int k = t.size() - 1; k >= 0 && out.size() < Live.KEEP; k--) out.add(t.get(k));
            return out;
        }
        return Live.recent();
    }

    /** Runs a slow job in the background and hands the answer back on the main thread. */
    public <T> void background(java.util.concurrent.Callable<T> job, Consumer<T> done) {
        work.execute(() -> {
            try {
                T r = job.call();
                main.post(() -> done.accept(r));
            } catch (Exception e) {
                android.util.Log.w("Jejak", "background job failed", e);
                main.post(() -> done.accept(null));
            }
        });
    }

    /** Removes readings older than the "Keep readings for" setting. */
    public void cleanUp() {
        int keep = keepDays();
        if (keep <= 0) return;
        db.deleteBefore(dayStart(LocalDate.now(ZoneId.systemDefault()).minusDays(keep)));
        changed();
    }

    public void deleteDay(LocalDate d) {
        db.deleteRange(dayStart(d), dayStart(d.plusDays(1)));
        changed();
    }

    public void deleteAll() {
        db.deleteAll();
        changed();
    }

}
