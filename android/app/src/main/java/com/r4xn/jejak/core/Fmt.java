package com.r4xn.jejak.core;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Numbers, distances, times and dates written the same way everywhere (and the same on iPhone). */
public final class Fmt {
    private static final long[] TEN = {1, 10, 100, 1_000, 10_000, 100_000, 1_000_000, 10_000_000, 100_000_000};
    private static final String[] MONTHS = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
    private static final String[] DAYS = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};

    private Fmt() {
    }

    /** {@code v} with exactly {@code decimals} digits after the point, halves rounded up. Empty when unknown. */
    public static String fixed(double v, int decimals) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "";
        long scale = TEN[decimals];
        long r = (long) Math.floor(Math.abs(v) * scale + 0.5);
        StringBuilder s = new StringBuilder();
        if (v < 0 && r != 0) s.append('-');
        s.append(r / scale);
        if (decimals > 0) {
            String f = Long.toString(r % scale);
            s.append('.');
            for (int k = f.length(); k < decimals; k++) s.append('0');
            s.append(f);
        }
        return s.toString();
    }

    /** "1,234,567" */
    public static String count(long n) {
        String s = Long.toString(Math.abs(n));
        StringBuilder b = new StringBuilder();
        for (int k = 0; k < s.length(); k++) {
            if (k > 0 && (s.length() - k) % 3 == 0) b.append(',');
            b.append(s.charAt(k));
        }
        return (n < 0 ? "-" : "") + b;
    }

    /** "850 m", "12.4 km", "128 km" */
    public static String distance(double m) {
        if (m < 100) return fixed(m, 0) + " m";
        if (m < 995) return fixed(Math.floor(m / 10 + 0.5) * 10, 0) + " m";
        if (m < 99_950) return fixed(m / 1000, 1) + " km";
        return count((long) Math.floor(m / 1000 + 0.5)) + " km";
    }

    /** "45 s", "8 min", "1 h 5 min", "12 h" */
    public static String duration(long ms) {
        long s = Math.max(0, ms) / 1000;
        if (s < 60) return s + " s";
        long min = s / 60, h = min / 60, m = min % 60;
        if (h == 0) return m + " min";
        return m == 0 ? h + " h" : h + " h " + m + " min";
    }

    /** "27 km/h", "4.5 km/h" */
    public static String speed(double mps) {
        if (Double.isNaN(mps)) return "–";
        double k = mps * 3.6;
        return (k < 10 ? fixed(k, 1) : fixed(k, 0)) + " km/h";
    }

    /** "52 m" above sea level. */
    public static String altitude(double m) {
        return Double.isNaN(m) ? "–" : fixed(m, 0) + " m";
    }

    /** "±6 m" */
    public static String accuracy(double m) {
        return Double.isNaN(m) ? "±?" : "±" + fixed(m, 0) + " m";
    }

    /** "3.12900, 101.67130" (5 decimals is about 1 metre). */
    public static String coords(double lat, double lon) {
        return fixed(lat, 5) + ", " + fixed(lon, 5);
    }

    private static ZonedDateTime at(long t, ZoneId zone) {
        return Instant.ofEpochMilli(t).atZone(zone);
    }

    /** "8:05 am" */
    public static String time(long t, ZoneId zone) {
        ZonedDateTime d = at(t, zone);
        int h = d.getHour() % 12 == 0 ? 12 : d.getHour() % 12;
        return h + ":" + two(d.getMinute()) + (d.getHour() < 12 ? " am" : " pm");
    }

    /** "08:05:09" */
    public static String clock(long t, ZoneId zone) {
        ZonedDateTime d = at(t, zone);
        return two(d.getHour()) + ":" + two(d.getMinute()) + ":" + two(d.getSecond());
    }

    /** "2026-11-26T08:05:09+08:00" */
    public static String isoLocal(long t, ZoneId zone) {
        ZonedDateTime d = at(t, zone);
        int off = d.getOffset().getTotalSeconds() / 60;
        String sign = off < 0 ? "-" : "+";
        off = Math.abs(off);
        return date(d.toLocalDate()) + "T" + clock(t, zone) + sign + two(off / 60) + ":" + two(off % 60);
    }

    /** "2026-11-26T00:05:09Z" */
    public static String isoUtc(long t) {
        return isoLocal(t, ZoneId.of("UTC")).replace("+00:00", "Z");
    }

    /** "2026-11-26" */
    public static String date(LocalDate d) {
        return d.getYear() + "-" + two(d.getMonthValue()) + "-" + two(d.getDayOfMonth());
    }

    /** "Thu 26 Nov 2026" */
    public static String day(LocalDate d) {
        return DAYS[d.getDayOfWeek().getValue() - 1] + " " + d.getDayOfMonth() + " " + MONTHS[d.getMonthValue() - 1] + " " + d.getYear();
    }

    /** "Today", "Yesterday" or "Thu 26 Nov 2026". */
    public static String dayName(LocalDate d, LocalDate today) {
        if (d.equals(today)) return "Today";
        if (d.equals(today.minusDays(1))) return "Yesterday";
        return day(d);
    }

    /** "4.7 MB" */
    public static String bytes(long b) {
        if (b < 1024) return b + " B";
        if (b < 1024 * 1024) return fixed(b / 1024.0, 0) + " KB";
        if (b < 1024L * 1024 * 1024) return fixed(b / 1024.0 / 1024.0, 1) + " MB";
        return fixed(b / 1024.0 / 1024.0 / 1024.0, 2) + " GB";
    }

    private static String two(int v) {
        return v < 10 ? "0" + v : Integer.toString(v);
    }
}
