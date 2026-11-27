import Foundation

/// Readings as a spreadsheet (CSV) or a GPS track (GPX) that maps and fitness apps can open.
public enum Export {
    public static let csvHeader = "time,latitude,longitude,altitude_m,accuracy_m,speed_kmh"

    public static func csvRow(_ p: Fix, _ zone: TimeZone) -> String {
        Fmt.isoLocal(p.t, zone) + "," + Fmt.fixed(p.lat, 7) + "," + Fmt.fixed(p.lon, 7) + ","
            + Fmt.fixed(p.alt, 1) + "," + Fmt.fixed(p.acc, 1) + "," + Fmt.fixed(p.speed * 3.6, 1)
    }

    public static func csv(_ pts: [Fix], _ zone: TimeZone) -> String {
        var out = ""
        out.reserveCapacity(pts.count * 70 + 64)
        out += csvHeader
        out += "\n"
        for p in pts {
            out += csvRow(p, zone)
            out += "\n"
        }
        return out
    }

    /// One track; a new segment starts after more than `gapMs` without readings.
    public static func gpx(_ pts: [Fix], _ name: String, _ gapMs: Int64) -> String {
        var out = ""
        out.reserveCapacity(pts.count * 110 + 256)
        out += "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        out += "<gpx version=\"1.1\" creator=\"Jejak\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n"
        out += "  <trk>\n    <name>" + xml(name) + "</name>\n"
        out += "    <trkseg>\n"
        var last: Fix?
        for p in pts {
            if let l = last, p.t - l.t > gapMs { out += "    </trkseg>\n    <trkseg>\n" }
            out += "      <trkpt lat=\"" + Fmt.fixed(p.lat, 7) + "\" lon=\"" + Fmt.fixed(p.lon, 7) + "\">"
            if p.hasAlt { out += "<ele>" + Fmt.fixed(p.alt, 1) + "</ele>" }
            out += "<time>" + Fmt.isoUtc(p.t) + "</time>"
            out += "</trkpt>\n"
            last = p
        }
        out += "    </trkseg>\n  </trk>\n</gpx>\n"
        return out
    }

    static func xml(_ s: String) -> String {
        s.replacingOccurrences(of: "&", with: "&amp;").replacingOccurrences(of: "<", with: "&lt;")
            .replacingOccurrences(of: ">", with: "&gt;").replacingOccurrences(of: "\"", with: "&quot;")
    }
}
