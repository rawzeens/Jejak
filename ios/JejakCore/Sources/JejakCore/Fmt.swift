import Foundation

/// Numbers, distances, times and dates written the same way everywhere (and the same as on Android).
public enum Fmt {
    private static let ten: [Int64] = [1, 10, 100, 1_000, 10_000, 100_000, 1_000_000, 10_000_000, 100_000_000]
    private static let months = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]
    private static let days = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]
    static let utc = TimeZone(secondsFromGMT: 0)!

    /// `v` with exactly `decimals` digits after the point, halves rounded up. Empty when unknown.
    public static func fixed(_ v: Double, _ decimals: Int) -> String {
        if v.isNaN || v.isInfinite { return "" }
        let scale = ten[decimals]
        let x = (abs(v) * Double(scale) + 0.5).rounded(.down)
        if x >= 9.0e18 { return String(v) }
        let r = Int64(x)
        var s = ""
        if v < 0 && r != 0 { s += "-" }
        s += String(r / scale)
        if decimals > 0 {
            let f = String(r % scale)
            s += "."
            if f.count < decimals { s += String(repeating: "0", count: decimals - f.count) }
            s += f
        }
        return s
    }

    /// "1,234,567"
    public static func count(_ n: Int) -> String {
        let s = String(abs(n))
        var b = ""
        for (k, ch) in s.enumerated() {
            if k > 0 && (s.count - k) % 3 == 0 { b += "," }
            b.append(ch)
        }
        return (n < 0 ? "-" : "") + b
    }

    /// "850 m", "12.4 km", "128 km"
    public static func distance(_ m: Double) -> String {
        if m < 100 { return fixed(m, 0) + " m" }
        if m < 995 { return fixed((m / 10 + 0.5).rounded(.down) * 10, 0) + " m" }
        if m < 99_950 { return fixed(m / 1000, 1) + " km" }
        return count(Int((m / 1000 + 0.5).rounded(.down))) + " km"
    }

    /// "45 s", "8 min", "1 h 5 min", "12 h"
    public static func duration(_ ms: Int64) -> String {
        let s = max(0, ms) / 1000
        if s < 60 { return "\(s) s" }
        let min = s / 60, h = min / 60, m = min % 60
        if h == 0 { return "\(m) min" }
        return m == 0 ? "\(h) h" : "\(h) h \(m) min"
    }

    /// "27 km/h", "4.5 km/h"
    public static func speed(_ mps: Double) -> String {
        if mps.isNaN { return "–" }
        let k = mps * 3.6
        return (k < 10 ? fixed(k, 1) : fixed(k, 0)) + " km/h"
    }

    /// "52 m" above sea level.
    public static func altitude(_ m: Double) -> String {
        m.isNaN ? "–" : fixed(m, 0) + " m"
    }

    /// "±6 m"
    public static func accuracy(_ m: Double) -> String {
        m.isNaN ? "±?" : "±" + fixed(m, 0) + " m"
    }

    /// "3.12900, 101.67130" (5 decimals is about 1 metre).
    public static func coords(_ lat: Double, _ lon: Double) -> String {
        fixed(lat, 5) + ", " + fixed(lon, 5)
    }

    /// The local date, hour, minute and second of a moment, and the zone's offset from UTC in seconds.
    public static func parts(_ t: Int64, _ zone: TimeZone) -> (day: Day, h: Int, m: Int, s: Int, offset: Int) {
        let secs = floorDiv(t, 1000)
        let off = zone.secondsFromGMT(for: Date(timeIntervalSince1970: TimeInterval(secs)))
        let local = secs + Int64(off)
        let d = floorDiv(local, 86_400)
        let sod = Int(local - d * 86_400)
        return (Day(epochDay: Int(d)), sod / 3600, sod % 3600 / 60, sod % 60, off)
    }

    /// "8:05 am"
    public static func time(_ t: Int64, _ zone: TimeZone) -> String {
        let p = parts(t, zone)
        let h = p.h % 12 == 0 ? 12 : p.h % 12
        return "\(h):" + two(p.m) + (p.h < 12 ? " am" : " pm")
    }

    /// "08:05:09"
    public static func clock(_ t: Int64, _ zone: TimeZone) -> String {
        let p = parts(t, zone)
        return two(p.h) + ":" + two(p.m) + ":" + two(p.s)
    }

    /// "2026-11-26T08:05:09+08:00"
    public static func isoLocal(_ t: Int64, _ zone: TimeZone) -> String {
        let p = parts(t, zone)
        var off = p.offset / 60
        let sign = off < 0 ? "-" : "+"
        off = abs(off)
        return date(p.day) + "T" + two(p.h) + ":" + two(p.m) + ":" + two(p.s) + sign + two(off / 60) + ":" + two(off % 60)
    }

    /// "2026-11-26T00:05:09Z"
    public static func isoUtc(_ t: Int64) -> String {
        isoLocal(t, utc).replacingOccurrences(of: "+00:00", with: "Z")
    }

    /// "2026-11-26"
    public static func date(_ d: Day) -> String {
        "\(d.year)-" + two(d.month) + "-" + two(d.day)
    }

    /// "Thu 26 Nov 2026"
    public static func day(_ d: Day) -> String {
        days[d.weekday] + " \(d.day) " + months[d.month - 1] + " \(d.year)"
    }

    /// "Today", "Yesterday" or "Thu 26 Nov 2026".
    public static func dayName(_ d: Day, _ today: Day) -> String {
        if d == today { return "Today" }
        if d == today.adding(-1) { return "Yesterday" }
        return day(d)
    }

    /// "4.7 MB"
    public static func bytes(_ b: Int64) -> String {
        if b < 1024 { return "\(b) B" }
        if b < 1024 * 1024 { return fixed(Double(b) / 1024.0, 0) + " KB" }
        if b < 1024 * 1024 * 1024 { return fixed(Double(b) / 1024.0 / 1024.0, 1) + " MB" }
        return fixed(Double(b) / 1024.0 / 1024.0 / 1024.0, 2) + " GB"
    }

    private static func two(_ v: Int) -> String {
        v < 10 ? "0\(v)" : String(v)
    }
}
