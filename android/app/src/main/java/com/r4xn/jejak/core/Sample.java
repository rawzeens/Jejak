package com.r4xn.jejak.core;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Made-up readings, one every second, for trying the app and for screenshots: weekdays go home → office → lunch →
 * office → gym → home (losing signal on the way back), weekends go to the shopping mall.
 * Uses whole-number maths only, so the iPhone app makes exactly the same readings.
 */
public final class Sample {
    public static final ZoneId ZONE = ZoneId.of("Asia/Kuala_Lumpur");
    /** How many days before today have sample readings. */
    public static final int DAYS_BACK = 13;

    // {latitude, longitude} in ten-millionths of a degree, height in tenths of a metre
    static final int[] HOME = {31290000, 1016713000, 520};
    static final int[] OFFICE = {31556000, 1017146000, 610};
    static final int[] LUNCH = {31530000, 1017105000, 590};
    static final int[] GYM = {31181000, 1016772000, 480};
    static final int[] MALL = {31490000, 1017134000, 640};

    // Trip legs: {latitude, longitude, height, seconds, signal (1 = yes)}, each from where the last one ended.
    static final int[][] TO_WORK = {
            {31312000, 1016745000, 530, 120, 1},
            {31312000, 1016745000, 530, 90, 1},   // traffic light
            {31345000, 1016862000, 560, 150, 1},
            {31418000, 1016951000, 600, 110, 1},
            {31418000, 1016951000, 600, 180, 1},  // jam
            {31489000, 1017040000, 580, 400, 1},
            {31540000, 1017118000, 600, 120, 1},
            {OFFICE[0], OFFICE[1], OFFICE[2], 90, 1},
    };
    static final int[][] TO_LUNCH = {
            {31540000, 1017150000, 600, 140, 1},
            {LUNCH[0], LUNCH[1], LUNCH[2], 380, 1},
    };
    static final int[][] FROM_LUNCH = {
            {31540000, 1017150000, 600, 380, 1},
            {OFFICE[0], OFFICE[1], OFFICE[2], 140, 1},
    };
    static final int[][] TO_GYM = {
            {31540000, 1017118000, 600, 90, 1},
            {31440000, 1017010000, 560, 130, 1},
            {31440000, 1017010000, 560, 120, 1},  // traffic light
            {31300000, 1016900000, 520, 150, 1},
            {GYM[0], GYM[1], GYM[2], 220, 1},
    };
    static final int[][] GYM_HOME = {
            {31230000, 1016700000, 500, 100, 1},
            {31262000, 1016690000, 510, 60, 1},
            {31330000, 1016790000, 540, 700, 0},  // no signal
            {HOME[0], HOME[1], HOME[2], 90, 1},
    };
    static final int[][] TO_MALL = {
            {31312000, 1016745000, 530, 100, 1},
            {31345000, 1016862000, 560, 110, 1},
            {31418000, 1016951000, 600, 100, 1},
            {31470000, 1017080000, 620, 140, 1},
            {MALL[0], MALL[1], MALL[2], 90, 1},
    };
    static final int[][] FROM_MALL = {
            {31470000, 1017080000, 620, 90, 1},
            {31418000, 1016951000, 600, 140, 1},
            {31345000, 1016862000, 560, 100, 1},
            {31312000, 1016745000, 530, 110, 1},
            {HOME[0], HOME[1], HOME[2], 100, 1},
    };

    private Sample() {
    }

    /** The places named in the sample (the lunch spot and the mall are left for the person to name). */
    public static List<Place> places() {
        List<Place> out = new ArrayList<>();
        out.add(new Place("place-1", "Home", HOME[0] / 1e7, HOME[1] / 1e7, 120));
        out.add(new Place("place-2", "Office", OFFICE[0] / 1e7, OFFICE[1] / 1e7, 150));
        out.add(new Place("place-3", "Gym", GYM[0] / 1e7, GYM[1] / 1e7, 100));
        return out;
    }

    public static boolean has(LocalDate date, LocalDate today) {
        return !date.isAfter(today) && !date.isBefore(today.minusDays(DAYS_BACK));
    }

    public static long start(LocalDate date) {
        return date.atStartOfDay(ZONE).toInstant().toEpochMilli();
    }

    /** The day's readings made before {@code until} (ms). */
    public static List<Fix> day(LocalDate date, long until) {
        Maker m = new Maker(start(date), until, date.toEpochDay() * 1000003L + 7);
        DayOfWeek w = date.getDayOfWeek();
        if (w == DayOfWeek.SATURDAY || w == DayOfWeek.SUNDAY) {
            m.stay(HOME, hms(10, 30));
            m.trip(TO_MALL);
            m.stay(MALL, hms(14, 10));
            m.trip(FROM_MALL);
        } else {
            m.stay(HOME, hms(7, 45));
            m.trip(TO_WORK);
            m.stay(OFFICE, hms(12, 30));
            m.trip(TO_LUNCH);
            m.stay(LUNCH, hms(13, 25));
            m.trip(FROM_LUNCH);
            m.stay(OFFICE, hms(18, 5));
            m.trip(TO_GYM);
            m.stay(GYM, hms(19, 45));
            m.trip(GYM_HOME);
        }
        m.stay(HOME, 86_400);
        return m.out;
    }

    static int hms(int h, int m) {
        return h * 3600 + m * 60;
    }

    /** Writes one reading per second. */
    private static final class Maker {
        final List<Fix> out = new ArrayList<>(86_400);
        final long dayStart, until;
        long state;
        int second;
        int lat, lon, alt;

        Maker(long dayStart, long until, long seed) {
            this.dayStart = dayStart;
            this.until = until;
            this.state = seed;
            lat = HOME[0];
            lon = HOME[1];
            alt = HOME[2];
        }

        int next(int bound) {
            state = state * 6364136223846793005L + 1442695040888963407L;
            return (int) ((state >>> 33) % bound);
        }

        int jitter(int r) {
            return next(2 * r + 1) - r;
        }

        /** Indoors at a place until the given second of the day. */
        void stay(int[] place, int untilSecond) {
            lat = place[0];
            lon = place[1];
            alt = place[2];
            while (second < untilSecond) {
                int missing = next(100), dLat = jitter(900), dLon = jitter(900), dAlt = jitter(20);
                int acc = 8 + next(23), speed = next(31), bad = next(1500);
                if (missing >= 15) {
                    if (bad == 0) emit(lat + 3000 + dLat, lon - 2500 + dLon, alt + dAlt, 160, speed / 100.0);
                    else emit(lat + dLat, lon + dLon, alt + dAlt, acc, speed / 100.0);
                }
                second++;
            }
        }

        /** Travelling along the legs, outdoors. */
        void trip(int[][] legs) {
            for (int[] leg : legs) {
                int fLat = lat, fLon = lon, fAlt = alt, secs = leg[3];
                double dy = (leg[0] - fLat) * 0.0111319491, dx = (leg[1] - fLon) * 0.0111319491 * 0.9985;
                double v = Math.sqrt(dx * dx + dy * dy) / secs;
                for (int k = 1; k <= secs; k++) {
                    int pLat = fLat + (int) ((long) (leg[0] - fLat) * k / secs);
                    int pLon = fLon + (int) ((long) (leg[1] - fLon) * k / secs);
                    int pAlt = fAlt + (leg[2] - fAlt) * k / secs;
                    int ignored = next(100), dLat = jitter(200), dLon = jitter(200), dAlt = jitter(8);
                    int acc = 3 + next(6), sj = next(31), bad = next(1500);
                    double speed = Math.floor(Math.max(0, v + (sj - 15) / 100.0) * 100 + 0.5) / 100;
                    if (leg[4] == 1) emit(pLat + dLat, pLon + dLon, pAlt + dAlt, acc, speed);
                    second++;
                }
                lat = leg[0];
                lon = leg[1];
                alt = leg[2];
            }
        }

        void emit(int latE7, int lonE7, int altDm, int acc, double speed) {
            long t = dayStart + second * 1000L;
            if (t >= until) return;
            out.add(new Fix(t, latE7 / 1e7, lonE7 / 1e7, altDm / 10.0, acc, speed));
        }
    }
}
