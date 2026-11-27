package com.r4xn.jejak.core;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.ZoneId;
import java.util.List;

/** Readings as a spreadsheet (CSV) or a GPS track (GPX) that maps and fitness apps can open. */
public final class Export {
    public static final String CSV_HEADER = "time,latitude,longitude,altitude_m,accuracy_m,speed_kmh";

    private Export() {
    }

    public static void csv(List<Fix> pts, ZoneId zone, Appendable out) throws IOException {
        out.append(CSV_HEADER).append('\n');
        for (Fix p : pts) out.append(csvRow(p, zone)).append('\n');
    }

    public static String csvRow(Fix p, ZoneId zone) {
        return Fmt.isoLocal(p.t, zone) + "," + Fmt.fixed(p.lat, 7) + "," + Fmt.fixed(p.lon, 7) + ","
                + Fmt.fixed(p.alt, 1) + "," + Fmt.fixed(p.acc, 1) + "," + Fmt.fixed(p.speed * 3.6, 1);
    }

    /** One track; a new segment starts after more than {@code gapMs} without readings. */
    public static void gpx(List<Fix> pts, String name, long gapMs, Appendable out) throws IOException {
        out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        out.append("<gpx version=\"1.1\" creator=\"Jejak\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n");
        out.append("  <trk>\n    <name>").append(xml(name)).append("</name>\n");
        out.append("    <trkseg>\n");
        Fix last = null;
        for (Fix p : pts) {
            if (last != null && p.t - last.t > gapMs) out.append("    </trkseg>\n    <trkseg>\n");
            out.append("      <trkpt lat=\"").append(Fmt.fixed(p.lat, 7)).append("\" lon=\"").append(Fmt.fixed(p.lon, 7)).append("\">");
            if (p.hasAlt()) out.append("<ele>").append(Fmt.fixed(p.alt, 1)).append("</ele>");
            out.append("<time>").append(Fmt.isoUtc(p.t)).append("</time>");
            out.append("</trkpt>\n");
            last = p;
        }
        out.append("    </trkseg>\n  </trk>\n</gpx>\n");
    }

    public static String csv(List<Fix> pts, ZoneId zone) {
        StringBuilder b = new StringBuilder(pts.size() * 70 + 64);
        try {
            csv(pts, zone, b);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return b.toString();
    }

    public static String gpx(List<Fix> pts, String name, long gapMs) {
        StringBuilder b = new StringBuilder(pts.size() * 110 + 256);
        try {
            gpx(pts, name, gapMs, b);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return b.toString();
    }

    static String xml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
