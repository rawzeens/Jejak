package com.r4xn.jejak.core;

import java.util.ArrayList;
import java.util.List;

/** Distances on the Earth and shortening a long track for drawing. */
public final class Geo {
    /** Mean radius of the Earth in metres. */
    public static final double EARTH = 6371008.8;
    /** Metres in one degree of latitude. */
    static final double DEGREE = EARTH * Math.PI / 180;

    private Geo() {
    }

    /** Straight-line distance in metres between two points (haversine). */
    public static double distance(double lat1, double lon1, double lat2, double lon2) {
        double p1 = Math.toRadians(lat1), p2 = Math.toRadians(lat2);
        double dp = p2 - p1, dl = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * EARTH * Math.asin(Math.min(1, Math.sqrt(a)));
    }

    public static double distance(Fix a, Fix b) {
        return distance(a.lat, a.lon, b.lat, b.lon);
    }

    /**
     * Length of the track from point {@code from} to {@code to} (inclusive). Small wobbles are left out: a step
     * only counts once the phone is at least {@code step} metres from where the last counted step ended.
     */
    public static double trackLength(List<Fix> pts, int from, int to, double step) {
        if (from < 0 || to >= pts.size() || to <= from) return 0;
        double total = 0;
        Fix ref = pts.get(from);
        for (int k = from + 1; k <= to; k++) {
            Fix p = pts.get(k);
            double d = distance(ref, p);
            if (d >= step) {
                total += d;
                ref = p;
            }
        }
        total += distance(ref, pts.get(to));
        return total;
    }

    /**
     * Keeps only the points needed to draw the track within {@code tolerance} metres (Douglas–Peucker).
     * A day of readings every second becomes a few hundred points that look the same on a map.
     */
    public static List<Fix> simplify(List<Fix> pts, int from, int to, double tolerance) {
        List<Fix> out = new ArrayList<>();
        if (from < 0 || to >= pts.size() || to < from) return out;
        if (to - from < 2) {
            for (int k = from; k <= to; k++) out.add(pts.get(k));
            return out;
        }
        Fix o = pts.get(from);
        double kx = DEGREE * Math.cos(Math.toRadians(o.lat)), ky = DEGREE;
        int n = to - from + 1;
        double[] x = new double[n], y = new double[n];
        for (int k = 0; k < n; k++) {
            Fix p = pts.get(from + k);
            x[k] = (p.lon - o.lon) * kx;
            y[k] = (p.lat - o.lat) * ky;
        }
        boolean[] keep = new boolean[n];
        keep[0] = true;
        keep[n - 1] = true;
        int[] stack = new int[2 * n];
        int top = 0;
        stack[top++] = 0;
        stack[top++] = n - 1;
        double tol2 = tolerance * tolerance;
        while (top > 0) {
            int b = stack[--top], a = stack[--top];
            double best = -1;
            int at = -1;
            double dx = x[b] - x[a], dy = y[b] - y[a], len2 = dx * dx + dy * dy;
            for (int k = a + 1; k < b; k++) {
                double d2;
                if (len2 == 0) {
                    double ex = x[k] - x[a], ey = y[k] - y[a];
                    d2 = ex * ex + ey * ey;
                } else {
                    double u = ((x[k] - x[a]) * dx + (y[k] - y[a]) * dy) / len2;
                    if (u < 0) u = 0;
                    else if (u > 1) u = 1;
                    double ex = x[a] + u * dx - x[k], ey = y[a] + u * dy - y[k];
                    d2 = ex * ex + ey * ey;
                }
                if (d2 > best) {
                    best = d2;
                    at = k;
                }
            }
            if (at >= 0 && best > tol2) {
                keep[at] = true;
                stack[top++] = a;
                stack[top++] = at;
                stack[top++] = at;
                stack[top++] = b;
            }
        }
        for (int k = 0; k < n; k++) if (keep[k]) out.add(pts.get(from + k));
        return out;
    }

    /** South-west and north-east corners around the points: {minLat, minLon, maxLat, maxLon}. */
    public static double[] bounds(List<Fix> pts) {
        if (pts.isEmpty()) return null;
        double a = 90, b = 180, c = -90, d = -180;
        for (Fix p : pts) {
            a = Math.min(a, p.lat);
            b = Math.min(b, p.lon);
            c = Math.max(c, p.lat);
            d = Math.max(d, p.lon);
        }
        return new double[]{a, b, c, d};
    }
}
