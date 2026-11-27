package com.r4xn.jejak.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Turns a day of GPS readings into a timeline: places the phone stayed at, the trips between them, and times
 * with no signal.
 */
public final class Timeline {
    public enum Kind {STAY, MOVE, GAP}

    public enum Mode {
        WALK("Walking"), CYCLE("Cycling"), VEHICLE("In a vehicle"), FLIGHT("Flying");

        public final String label;

        Mode(String label) {
            this.label = label;
        }
    }

    /** How readings are read. */
    public static final class Rules {
        /** Readings that may be further off than this (metres) are left out of the timeline. */
        public double maxAccuracy = 65;
        /** Readings within this distance (metres) of each other count as the same place. */
        public double stayRadius = 100;
        /** Staying in one place at least this long (ms) makes it a stop. */
        public long minStay = 5 * 60_000L;
        /** No readings for longer than this (ms) while travelling is shown as "No signal". */
        public long gap = 10 * 60_000L;
        /** Faster than this (m/s) from the reading before is a glitch, not real movement. */
        public double glitchSpeed = 340;
    }

    /** One line of the timeline. */
    public static final class Item {
        public final Kind kind;
        public final long start, end;
        /** Readings in {@link Day#points} that belong to this line (inclusive); -1 for a gap. */
        public final int from, to;
        /** Stop: the middle of the place. Trip: where it ended. */
        public double lat, lon;
        /** Trip distance in metres. */
        public double meters;
        /** Trip: a fast but typical speed (95th percentile, m/s). */
        public double fast;
        /** Trip: metres climbed and descended. */
        public double climb, descent;
        public Mode mode;

        Item(Kind kind, long start, long end, int from, int to) {
            this.kind = kind;
            this.start = start;
            this.end = end;
            this.from = from;
            this.to = to;
        }

        public long duration() {
            return end - start;
        }

        /** Average speed in m/s. */
        public double average() {
            return duration() <= 0 ? 0 : meters / (duration() / 1000.0);
        }

        public int count() {
            return from < 0 ? 0 : to - from + 1;
        }
    }

    /** A day read as a timeline. */
    public static final class Day {
        /** Readings used, in time order (glitches and inaccurate ones left out). */
        public final List<Fix> points;
        /** All readings that day. */
        public final int raw;
        public final List<Item> items;
        public double meters;
        public long moving, still;
        public int stops;
        public double minAlt = Double.NaN, maxAlt = Double.NaN, fastest;

        Day(List<Fix> points, int raw, List<Item> items) {
            this.points = points;
            this.raw = raw;
            this.items = items;
        }

        public List<Item> stops() {
            List<Item> out = new ArrayList<>();
            for (Item i : items) if (i.kind == Kind.STAY) out.add(i);
            return out;
        }
    }

    private Timeline() {
    }

    /** Readings in time order without the inaccurate ones and glitches. */
    public static List<Fix> clean(List<Fix> raw, Rules r) {
        List<Fix> sorted = new ArrayList<>(raw);
        sorted.sort((a, b) -> Long.compare(a.t, b.t));
        List<Fix> out = new ArrayList<>();
        Fix last = null;
        for (Fix p : sorted) {
            if (p.hasAcc() && p.acc > r.maxAccuracy) continue;
            if (Double.isNaN(p.lat) || Double.isNaN(p.lon) || Math.abs(p.lat) > 90 || Math.abs(p.lon) > 180) continue;
            if (last != null) {
                if (p.t == last.t) continue;
                double secs = Math.max(1, (p.t - last.t) / 1000.0);
                if (Geo.distance(last, p) / secs > r.glitchSpeed) continue;
            }
            out.add(p);
            last = p;
        }
        return out;
    }

    public static Day build(List<Fix> raw, Rules r) {
        List<Fix> pts = clean(raw, r);
        int n = pts.size();

        // 1. Stops: runs of readings that stay within the radius for long enough.
        List<int[]> found = new ArrayList<>();
        int i = 0;
        while (i < n) {
            Fix a = pts.get(i);
            int j = i + 1;
            while (j < n && Geo.distance(a, pts.get(j)) <= r.stayRadius) j++;
            if (pts.get(j - 1).t - a.t >= r.minStay) {
                // Carry on from the middle of the place, which is steadier than its first reading.
                double[] c = centre(pts, i, j - 1);
                while (j < n && Geo.distance(c[0], c[1], pts.get(j).lat, pts.get(j).lon) <= r.stayRadius) j++;
                found.add(new int[]{i, j - 1});
                i = j;
            } else {
                i++;
            }
        }

        // 2. Leaving a place for a few minutes and coming back is still one stop.
        List<int[]> stays = new ArrayList<>();
        for (int[] s : found) {
            if (!stays.isEmpty()) {
                int[] p = stays.get(stays.size() - 1);
                double[] cp = centre(pts, p[0], p[1]), cs = centre(pts, s[0], s[1]);
                long away = pts.get(s[0]).t - pts.get(p[1]).t;
                if (away <= r.minStay && Geo.distance(cp[0], cp[1], cs[0], cs[1]) <= r.stayRadius) {
                    p[1] = s[1];
                    continue;
                }
            }
            stays.add(s);
        }

        // 3. Stops, with the trips in between.
        List<Item> lines = new ArrayList<>();
        int next = 0;
        for (int[] s : stays) {
            if (next < s[0]) trips(pts, next, s[0] - 1, r, lines);
            lines.add(stay(pts, s[0], s[1]));
            next = s[1] + 1;
        }
        if (next < n) trips(pts, next, n - 1, r, lines);

        // 4. Long silences between lines.
        List<Item> items = new ArrayList<>();
        for (Item it : lines) {
            if (!items.isEmpty()) {
                Item prev = items.get(items.size() - 1);
                if (it.start - prev.end > r.gap) items.add(new Item(Kind.GAP, prev.end, it.start, -1, -1));
            }
            items.add(it);
        }

        Day day = new Day(pts, raw.size(), items);
        for (Item it : items) {
            if (it.kind == Kind.STAY) {
                day.stops++;
                day.still += it.duration();
            } else if (it.kind == Kind.MOVE) {
                day.meters += it.meters;
                day.moving += it.duration();
                day.fastest = Math.max(day.fastest, it.fast);
            }
        }
        for (Fix p : pts) {
            if (!p.hasAlt()) continue;
            if (Double.isNaN(day.minAlt) || p.alt < day.minAlt) day.minAlt = p.alt;
            if (Double.isNaN(day.maxAlt) || p.alt > day.maxAlt) day.maxAlt = p.alt;
        }
        return day;
    }

    /** Average position of readings {@code a..b}: {lat, lon}. */
    static double[] centre(List<Fix> pts, int a, int b) {
        double la = 0, lo = 0;
        for (int k = a; k <= b; k++) {
            la += pts.get(k).lat;
            lo += pts.get(k).lon;
        }
        int c = b - a + 1;
        return new double[]{la / c, lo / c};
    }

    private static Item stay(List<Fix> pts, int a, int b) {
        Item it = new Item(Kind.STAY, pts.get(a).t, pts.get(b).t, a, b);
        double[] c = centre(pts, a, b);
        it.lat = c[0];
        it.lon = c[1];
        return it;
    }

    /** Readings {@code from..to} lie between stops: one trip, or several when the signal dropped for a while. */
    private static void trips(List<Fix> pts, int from, int to, Rules r, List<Item> out) {
        int start = from;
        for (int k = from + 1; k <= to + 1; k++) {
            if (k <= to && pts.get(k).t - pts.get(k - 1).t <= r.gap) continue;
            int a = start, b = k - 1;
            // Join the trip to the stop before and after it, unless the signal was lost in between.
            if (a == from && a > 0 && pts.get(a).t - pts.get(a - 1).t <= r.gap) a--;
            if (b == to && b + 1 < pts.size() && pts.get(b + 1).t - pts.get(b).t <= r.gap) b++;
            Item t = trip(pts, a, b);
            if (t.meters >= 30) out.add(t);
            start = k;
        }
    }

    private static Item trip(List<Fix> pts, int a, int b) {
        Item it = new Item(Kind.MOVE, pts.get(a).t, pts.get(b).t, a, b);
        it.lat = pts.get(b).lat;
        it.lon = pts.get(b).lon;
        it.meters = Geo.trackLength(pts, a, b, 8);
        it.fast = fastSpeed(pts, a, b);
        if (it.fast == 0) it.fast = it.average();
        it.mode = it.fast < 2.6 ? Mode.WALK : it.fast < 7.5 ? Mode.CYCLE : it.fast < 70 ? Mode.VEHICLE : Mode.FLIGHT;
        double ref = Double.NaN;
        for (int k = a; k <= b; k++) {
            Fix p = pts.get(k);
            if (!p.hasAlt()) continue;
            if (Double.isNaN(ref)) {
                ref = p.alt;
            } else if (p.alt - ref >= 4) {
                it.climb += p.alt - ref;
                ref = p.alt;
            } else if (ref - p.alt >= 4) {
                it.descent += ref - p.alt;
                ref = p.alt;
            }
        }
        return it;
    }

    /**
     * A fast but typical speed for a trip (95th percentile, m/s): the higher of the phone's own speed readings and
     * the speed worked out from positions 10 seconds apart (second-by-second positions wobble too much). Using both
     * copes with phones that report a speed of 0 while moving, and with readings that have no speed at all.
     */
    static double fastSpeed(List<Fix> pts, int a, int b) {
        double[] own = new double[b - a + 1];
        int n = 0;
        for (int k = a; k <= b; k++) if (pts.get(k).hasSpeed()) own[n++] = pts.get(k).speed;
        double[] moved = new double[b - a + 1];
        int c = 0;
        int m = a;
        for (int k = a; k <= b; k++) {
            if (m < k) m = k;
            while (m <= b && pts.get(m).t - pts.get(k).t < 10_000) m++;
            if (m > b) break;
            double dt = (pts.get(m).t - pts.get(k).t) / 1000.0;
            if (dt <= 60) moved[c++] = Geo.distance(pts.get(k), pts.get(m)) / dt;
        }
        return Math.max(percentile95(own, n), percentile95(moved, c));
    }

    private static double percentile95(double[] v, int count) {
        if (count == 0) return 0;
        double[] s = Arrays.copyOf(v, count);
        Arrays.sort(s);
        return s[(int) Math.floor((count - 1) * 0.95)];
    }

    /** The saved place a stop is at, if any. */
    public static Place placeOf(Item stop, List<Place> places) {
        return stop.kind == Kind.STAY ? Place.nearest(places, stop.lat, stop.lon) : null;
    }
}
