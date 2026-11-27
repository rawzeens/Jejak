package com.r4xn.jejak.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public class TimelineTest {
    static final ZoneId KL = Sample.ZONE;
    /** Thursday 26 November 2026. */
    static final LocalDate THU = LocalDate.of(2026, 11, 26);
    /** Sunday 22 November 2026. */
    static final LocalDate SUN = LocalDate.of(2026, 11, 22);
    static final Timeline.Rules RULES = new Timeline.Rules();

    static long at(LocalDate d, int h, int m) {
        return Sample.start(d) + (h * 3600L + m * 60L) * 1000L;
    }

    static String describe(Timeline.Day day) {
        StringBuilder b = new StringBuilder();
        for (Timeline.Item i : day.items) {
            Place p = Timeline.placeOf(i, Sample.places());
            b.append(i.kind).append(' ').append(Fmt.clock(i.start, KL)).append('-').append(Fmt.clock(i.end, KL)).append(' ')
                    .append(i.kind == Timeline.Kind.MOVE ? i.mode + " " + Fmt.distance(i.meters) + " " + Fmt.speed(i.fast) : "")
                    .append(p != null ? p.name : "").append('\n');
        }
        return b.toString();
    }

    @Test
    public void weekdayReadsAsStopsAndTrips() {
        List<Fix> raw = Sample.day(THU, Long.MAX_VALUE);
        Timeline.Day day = Timeline.build(raw, RULES);
        System.out.println(describe(day));
        String[] kinds = {"STAY Home", "MOVE VEHICLE", "STAY Office", "MOVE WALK", "STAY ", "MOVE WALK", "STAY Office",
                "MOVE VEHICLE", "STAY Gym", "MOVE VEHICLE", "GAP", "MOVE VEHICLE", "STAY Home"};
        assertEquals(describe(day), kinds.length, day.items.size());
        for (int k = 0; k < kinds.length; k++) {
            Timeline.Item i = day.items.get(k);
            Place p = Timeline.placeOf(i, Sample.places());
            String got = i.kind + (i.kind == Timeline.Kind.MOVE ? " " + i.mode : i.kind == Timeline.Kind.STAY ? " " + (p == null ? "" : p.name) : "");
            assertEquals("line " + k, kinds[k], got);
        }
        // Leaves home at 7:45, back at about 8 pm.
        Timeline.Item home = day.items.get(0), work = day.items.get(1), back = day.items.get(12);
        assertTrue(home.start - Sample.start(THU) < 10_000);
        assertTrue(Math.abs(home.end - at(THU, 7, 45)) < 90_000);
        assertTrue(Math.abs(work.end - at(THU, 8, 6)) < 90_000);
        assertTrue(Math.abs(back.start - at(THU, 20, 0)) < 120_000);
        // The drive to work is about 6 km.
        assertTrue(Fmt.distance(work.meters), work.meters > 5500 && work.meters < 7000);
        // The no-signal stretch on the way home.
        Timeline.Item gap = day.items.get(10);
        assertTrue(gap.duration() > 10 * 60_000L && gap.duration() < 13 * 60_000L);
        assertEquals(6, day.stops);
        assertTrue(Fmt.distance(day.meters), day.meters > 12_000 && day.meters < 20_000);
        // Inaccurate indoor readings are dropped; about one second in seven indoors has no reading.
        assertTrue(day.raw > 70_000 && day.raw < 80_000);
        assertTrue(day.points.size() < day.raw);
        assertTrue(back.end > Sample.start(THU) + 86_400_000L - 60_000L);
    }

    @Test
    public void weekendGoesToTheMall() {
        Timeline.Day day = Timeline.build(Sample.day(SUN, Long.MAX_VALUE), RULES);
        String d = describe(day);
        assertEquals(d, 5, day.items.size());
        assertEquals(d, Timeline.Kind.STAY, day.items.get(2).kind);
        assertNull(Timeline.placeOf(day.items.get(2), Sample.places()));
        assertEquals(Timeline.Mode.VEHICLE, day.items.get(1).mode);
        assertEquals(Timeline.Mode.VEHICLE, day.items.get(3).mode);
    }

    @Test
    public void todaySoFar() {
        Timeline.Day day = Timeline.build(Sample.day(THU, at(THU, 9, 41)), RULES);
        assertEquals(describe(day), 3, day.items.size());
        assertEquals(Timeline.Kind.STAY, day.items.get(2).kind);
        assertTrue(day.items.get(2).end < at(THU, 9, 41));
        assertTrue(Sample.day(THU, Sample.start(THU)).isEmpty());
    }

    @Test
    public void glitchesAndInaccurateReadingsAreLeftOut() {
        long t0 = 1_000_000_000_000L;
        List<Fix> raw = new ArrayList<>();
        for (int s = 0; s < 600; s++) raw.add(new Fix(t0 + s * 1000L, 3.1, 101.6, 50, 5, 0));
        raw.add(new Fix(t0 + 600_500L, 3.2, 101.6, 50, 5, 0));          // 11 km in half a second
        raw.add(new Fix(t0 + 601_000L, 3.1001, 101.6, 50, 500, 0));      // ±500 m
        raw.add(new Fix(t0 + 5_000L, 3.1, 101.6, 50, 5, 0));             // same second twice
        List<Fix> clean = Timeline.clean(raw, RULES);
        assertEquals(600, clean.size());
        Timeline.Day day = Timeline.build(raw, RULES);
        assertEquals(1, day.items.size());
        assertEquals(Timeline.Kind.STAY, day.items.get(0).kind);
        assertEquals(603, day.raw);
    }

    @Test
    public void shortStopsAreNotPlaces() {
        // Walk 1.4 m/s east for 10 minutes, stand 3 minutes, walk on 10 minutes: one trip, no stop.
        long t0 = 1_000_000_000_000L;
        List<Fix> raw = new ArrayList<>();
        double lon = 101.6;
        int s = 0;
        for (; s < 600; s++, lon += 1.4 / 111_000) raw.add(new Fix(t0 + s * 1000L, 3.1, lon, 50, 5, 1.4));
        for (; s < 780; s++) raw.add(new Fix(t0 + s * 1000L, 3.1, lon, 50, 5, 0));
        for (; s < 1380; s++, lon += 1.4 / 111_000) raw.add(new Fix(t0 + s * 1000L, 3.1, lon, 50, 5, 1.4));
        Timeline.Day day = Timeline.build(raw, RULES);
        assertEquals(1, day.items.size());
        Timeline.Item walk = day.items.get(0);
        assertEquals(Timeline.Mode.WALK, walk.mode);
        assertTrue(Fmt.distance(walk.meters), Math.abs(walk.meters - 1680) < 40);
        assertEquals(0, day.stops);
    }

    @Test
    public void distancesAndShortening() {
        // Kuala Lumpur to Penang is about 300 km in a straight line.
        double d = Geo.distance(3.1390, 101.6869, 5.4141, 100.3288);
        assertTrue(d > 290_000 && d < 300_000);
        assertEquals(0, Geo.distance(3.1, 101.6, 3.1, 101.6), 1e-9);
        List<Fix> day = Timeline.build(Sample.day(THU, Long.MAX_VALUE), RULES).points;
        List<Fix> s = Geo.simplify(day, 0, day.size() - 1, 15);
        assertTrue(s.size() + " points", s.size() < day.size() / 20);
        assertEquals(day.get(0), s.get(0));
        assertEquals(day.get(day.size() - 1), s.get(s.size() - 1));
        double[] b = Geo.bounds(day);
        assertTrue(b[0] < 3.12 && b[2] > 3.155);
    }

    @Test
    public void namedPlaces() {
        List<Place> places = Sample.places();
        assertEquals("Home", Place.nearest(places, 3.1291, 101.6714).name);
        assertNull(Place.nearest(places, 3.2, 101.6));
        assertEquals("place-4", Place.newId(places));
        List<Place> back = Place.fromJson(Place.toJson(places));
        assertEquals(3, back.size());
        assertEquals("Office", back.get(1).name);
        assertEquals(150, back.get(1).radius, 0);
        assertTrue(Place.fromJson("not json").isEmpty());
    }

    @Test
    public void writing() {
        assertEquals("1.3", Fmt.fixed(1.25, 1));
        assertEquals("-0.5", Fmt.fixed(-0.45, 1));
        assertEquals("0", Fmt.fixed(-0.4, 0));
        assertEquals("3.1290000", Fmt.fixed(3.129, 7));
        assertEquals("", Fmt.fixed(Double.NaN, 1));
        assertEquals("1,234,567", Fmt.count(1234567));
        assertEquals("45 m", Fmt.distance(45.4));
        assertEquals("850 m", Fmt.distance(847));
        assertEquals("1.0 km", Fmt.distance(996));
        assertEquals("12.4 km", Fmt.distance(12_420));
        assertEquals("128 km", Fmt.distance(127_600));
        assertEquals("45 s", Fmt.duration(45_000));
        assertEquals("8 min", Fmt.duration(8 * 60_000 + 59_000));
        assertEquals("1 h 5 min", Fmt.duration(65 * 60_000));
        assertEquals("12 h", Fmt.duration(12 * 3600_000));
        assertEquals("4.5 km/h", Fmt.speed(1.25));
        assertEquals("27 km/h", Fmt.speed(7.5));
        long t = at(THU, 8, 5) + 9000;
        assertEquals("8:05 am", Fmt.time(t, KL));
        assertEquals("12:00 am", Fmt.time(Sample.start(THU), KL));
        assertEquals("12:30 pm", Fmt.time(at(THU, 12, 30), KL));
        assertEquals("08:05:09", Fmt.clock(t, KL));
        assertEquals("2026-11-26T08:05:09+08:00", Fmt.isoLocal(t, KL));
        assertEquals("2026-11-26T00:05:09Z", Fmt.isoUtc(t));
        assertEquals("Thu 26 Nov 2026", Fmt.day(THU));
        assertEquals("Yesterday", Fmt.dayName(THU.minusDays(1), THU));
        assertEquals("4.7 MB", Fmt.bytes(4_928_307));
        assertEquals("3.12900, 101.67130", Fmt.coords(3.129, 101.6713));
    }

    @Test
    public void exports() {
        Fix f = new Fix(at(THU, 8, 5) + 9000, 3.129, 101.6713, 52.25, 6, 7.5);
        assertEquals("2026-11-26T08:05:09+08:00,3.1290000,101.6713000,52.3,6.0,27.0", Export.csvRow(f, KL));
        Fix noAlt = new Fix(at(THU, 8, 5), 3.129, 101.6713, Double.NaN, Double.NaN, Double.NaN);
        assertEquals("2026-11-26T08:05:00+08:00,3.1290000,101.6713000,,,", Export.csvRow(noAlt, KL));
        List<Fix> two = List.of(noAlt, f, new Fix(f.t + 3600_000, 3.13, 101.67, 50, 5, 0));
        String gpx = Export.gpx(two, "Jejak & me", 600_000);
        assertTrue(gpx.contains("<name>Jejak &amp; me</name>"));
        assertTrue(gpx.contains("<trkpt lat=\"3.1290000\" lon=\"101.6713000\"><time>2026-11-26T00:05:00Z</time></trkpt>"));
        assertTrue(gpx.contains("<ele>52.3</ele>"));
        assertEquals(2, gpx.split("<trkseg>", -1).length - 1);
        String csv = Export.csv(two, KL);
        assertEquals(4, csv.split("\n").length);
        assertTrue(csv.startsWith(Export.CSV_HEADER + "\n"));
    }

    @Test
    public void storedAsWholeNumbers() {
        Fix f = new Fix(5, 3.1290012, 101.6713005, 52.25, 6.04, 1.234);
        Fix back = Fix.stored(f.t, f.latE7(), f.lonE7(), Fix.scaled(f.alt, 10), Fix.scaled(f.acc, 10), Fix.scaled(f.speed, 100));
        assertEquals(31290012, f.latE7());
        assertEquals(f.lat, back.lat, 1e-9);
        assertEquals(52.3, back.alt, 1e-9);
        assertEquals(6.0, back.acc, 1e-9);
        assertEquals(1.23, back.speed, 1e-9);
        assertNull(Fix.scaled(Double.NaN, 10));
        assertTrue(Double.isNaN(Fix.stored(1, 0, 0, null, null, null).alt));
    }

    // ------------------------------------------------------------------ for the iPhone app's checks

    static String fnv(String s) {
        long h = 0xcbf29ce484222325L;
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            h ^= (b & 0xff);
            h *= 0x100000001b3L;
        }
        return Long.toUnsignedString(h, 16);
    }

    static JSONObject dayJson(LocalDate d, long until) throws Exception {
        List<Fix> raw = Sample.day(d, until);
        Timeline.Day day = Timeline.build(raw, RULES);
        JSONObject o = new JSONObject();
        o.put("date", Fmt.date(d));
        o.put("until", until == Long.MAX_VALUE ? -1 : until);
        o.put("raw", day.raw);
        o.put("kept", day.points.size());
        o.put("meters", day.meters);
        o.put("moving", day.moving);
        o.put("still", day.still);
        o.put("stops", day.stops);
        o.put("minAlt", day.minAlt);
        o.put("maxAlt", day.maxAlt);
        o.put("fastest", day.fastest);
        JSONArray items = new JSONArray();
        for (Timeline.Item i : day.items) {
            JSONObject it = new JSONObject();
            it.put("kind", i.kind.name());
            it.put("start", i.start);
            it.put("end", i.end);
            it.put("from", i.from);
            it.put("to", i.to);
            it.put("lat", i.lat);
            it.put("lon", i.lon);
            it.put("meters", i.meters);
            it.put("fast", i.fast);
            it.put("climb", i.climb);
            it.put("descent", i.descent);
            it.put("mode", i.mode == null ? "" : i.mode.name());
            Place p = Timeline.placeOf(i, Sample.places());
            it.put("place", p == null ? "" : p.name);
            it.put("text", Fmt.time(i.start, KL) + " – " + Fmt.time(i.end, KL) + " · " + Fmt.duration(i.duration())
                    + (i.kind == Timeline.Kind.MOVE ? " · " + Fmt.distance(i.meters) + " · " + Fmt.speed(i.average()) : ""));
            items.put(it);
        }
        o.put("items", items);
        o.put("simplified", Geo.simplify(day.points, 0, day.points.size() - 1, 15).size());
        String csv = Export.csv(raw, KL);
        String gpx = Export.gpx(day.points, "Jejak " + Fmt.date(d), RULES.gap);
        o.put("csvLength", csv.length());
        o.put("csvHash", fnv(csv));
        o.put("gpxHash", fnv(gpx));
        JSONArray head = new JSONArray();
        String[] lines = csv.split("\n", 6);
        for (int k = 0; k < 5 && k < lines.length; k++) head.put(lines[k]);
        o.put("csvHead", head);
        return o;
    }

    @Test
    public void writesSampleForIphoneChecks() throws Exception {
        JSONObject o = new JSONObject();
        JSONArray days = new JSONArray();
        days.put(dayJson(THU, Long.MAX_VALUE));
        days.put(dayJson(SUN, Long.MAX_VALUE));
        days.put(dayJson(THU, at(THU, 9, 41)));
        o.put("days", days);
        java.io.File out = new java.io.File("build/android-sample.json");
        out.getParentFile().mkdirs();
        java.nio.file.Files.write(out.toPath(), o.toString(1).getBytes(StandardCharsets.UTF_8));
        assertTrue(out.length() > 1000);
    }
}
