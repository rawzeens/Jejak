package com.r4xn.jejak.core;

/** One GPS reading: where the phone was at one moment. */
public final class Fix {
    /** When, in milliseconds since 1 January 1970 (UTC). */
    public final long t;
    /** Latitude in degrees (north is positive). */
    public final double lat;
    /** Longitude in degrees (east is positive). */
    public final double lon;
    /** Height above sea level in metres, or NaN when the phone didn't give one. */
    public final double alt;
    /** How far off the reading may be, in metres, or NaN when unknown. */
    public final double acc;
    /** Speed in metres per second, or NaN when unknown. */
    public final double speed;

    public Fix(long t, double lat, double lon, double alt, double acc, double speed) {
        this.t = t;
        this.lat = lat;
        this.lon = lon;
        this.alt = alt;
        this.acc = acc;
        this.speed = speed;
    }

    public boolean hasAlt() {
        return !Double.isNaN(alt);
    }

    public boolean hasAcc() {
        return !Double.isNaN(acc);
    }

    public boolean hasSpeed() {
        return !Double.isNaN(speed);
    }

    // The database keeps whole numbers (about half the space of decimals).

    /** Latitude in ten-millionths of a degree (about 1 cm). */
    public int latE7() {
        return (int) Math.floor(lat * 1e7 + 0.5);
    }

    public int lonE7() {
        return (int) Math.floor(lon * 1e7 + 0.5);
    }

    /** A decimal value in tenths (or hundredths…), or null when unknown. */
    public static Integer scaled(double v, int scale) {
        return Double.isNaN(v) ? null : (int) Math.floor(v * scale + 0.5);
    }

    public static Fix stored(long t, int latE7, int lonE7, Integer altDm, Integer accDm, Integer speedCms) {
        return new Fix(t, latE7 / 1e7, lonE7 / 1e7,
                altDm == null ? Double.NaN : altDm / 10.0,
                accDm == null ? Double.NaN : accDm / 10.0,
                speedCms == null ? Double.NaN : speedCms / 100.0);
    }

    @Override
    public String toString() {
        return t + " " + lat + "," + lon + " alt " + alt + " ±" + acc + " " + speed + " m/s";
    }
}
