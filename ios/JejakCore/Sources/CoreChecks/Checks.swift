import Foundation
import JejakCore

/// The same checks as the Android app's tests, plus a comparison with readings and timelines the Android app
/// wrote (android-sample.json). Returns what failed (empty when all is well).
public enum Checks {
    final class Run {
        var failures: [String] = []
        var count = 0

        func expect(_ ok: Bool, _ what: @autoclosure () -> String, line: Int = #line) {
            count += 1
            if !ok { failures.append("line \(line): \(what())") }
        }

        func equal<T: Equatable>(_ a: T, _ b: T, _ what: String = "", line: Int = #line) {
            count += 1
            if a != b { failures.append("line \(line): \(what) expected \(b), got \(a)") }
        }

        func close(_ a: Double, _ b: Double, _ what: String = "", line: Int = #line) {
            count += 1
            let same = (a.isNaN && b.isNaN) || abs(a - b) <= 1e-6 * max(1, abs(b))
            if !same { failures.append("line \(line): \(what) expected \(b), got \(a)") }
        }
    }

    static let kl = Sample.zone
    /// Thursday 26 November 2026.
    static let thu = Day(2026, 11, 26)
    /// Sunday 22 November 2026.
    static let sun = Day(2026, 11, 22)
    static let rules = Timeline.Rules()

    static func at(_ d: Day, _ h: Int, _ m: Int) -> Int64 {
        Sample.start(d) + Int64(h * 3600 + m * 60) * 1000
    }

    static func describe(_ day: Timeline.Day) -> String {
        day.items.map { i -> String in
            let p = Timeline.placeOf(i, Sample.places())
            let move = i.kind == .move ? "\(i.mode?.rawValue ?? "") \(Fmt.distance(i.meters)) \(Fmt.speed(i.fast))" : ""
            return "\(i.kind.rawValue) \(Fmt.clock(i.start, kl))-\(Fmt.clock(i.end, kl)) \(move)\(p?.name ?? "")"
        }.joined(separator: "\n")
    }

    public static func runAll() -> (passed: Int, failures: [String]) {
        let r = Run()
        dates(r)
        weekday(r)
        weekend(r)
        todaySoFar(r)
        glitches(r)
        shortStops(r)
        distances(r)
        places(r)
        writing(r)
        exports(r)
        wholeNumbers(r)
        store(r)
        sameAsAndroid(r)
        return (r.count - r.failures.count, r.failures)
    }

    static func dates(_ r: Run) {
        r.equal(Day(1970, 1, 1).epochDay, 0)
        r.equal(thu.weekday, 3, "26 Nov 2026 is a Thursday")
        r.equal(sun.weekday, 6, "22 Nov 2026 is a Sunday")
        r.equal(Day(epochDay: thu.epochDay), thu)
        r.equal(Day(2024, 2, 28).adding(1), Day(2024, 2, 29))
        r.equal(Day(2024, 12, 31).adding(1), Day(2025, 1, 1))
        r.equal(Day(2000, 3, 1).adding(-1), Day(2000, 2, 29))
        r.equal(Sample.start(thu), 1_795_622_400_000, "midnight in Kuala Lumpur")
        r.equal(Day.of(at(thu, 23, 59), kl), thu)
        r.equal(Day.of(Sample.start(thu) - 1, kl), thu.adding(-1))
        r.expect(Day(2026, 1, 1) < thu, "dates compare")
    }

    static func weekday(_ r: Run) {
        let raw = Sample.day(thu, until: Int64.max)
        let day = Timeline.build(raw, rules)
        let kinds = ["STAY Home", "MOVE VEHICLE", "STAY Office", "MOVE WALK", "STAY ", "MOVE WALK", "STAY Office",
                     "MOVE VEHICLE", "STAY Gym", "MOVE VEHICLE", "GAP", "MOVE VEHICLE", "STAY Home"]
        r.equal(day.items.count, kinds.count, describe(day))
        guard day.items.count == kinds.count else { return }
        for (k, want) in kinds.enumerated() {
            let i = day.items[k]
            let p = Timeline.placeOf(i, Sample.places())
            let got = i.kind.rawValue + (i.kind == .move ? " " + (i.mode?.rawValue ?? "") : i.kind == .stay ? " " + (p?.name ?? "") : "")
            r.equal(got, want, "line \(k)")
        }
        // Leaves home at 7:45, back at about 8 pm.
        let home = day.items[0], work = day.items[1], back = day.items[12]
        r.expect(home.start - Sample.start(thu) < 10_000, "day starts at midnight")
        r.expect(abs(home.end - at(thu, 7, 45)) < 90_000, "leaves home at 7:45")
        r.expect(abs(work.end - at(thu, 8, 6)) < 90_000, "at work by 8:06")
        r.expect(abs(back.start - at(thu, 20, 0)) < 120_000, "home at 8 pm")
        // The drive to work is about 6 km.
        r.expect(work.meters > 5500 && work.meters < 7000, Fmt.distance(work.meters))
        // The no-signal stretch on the way home.
        let gap = day.items[10]
        r.expect(gap.duration > 10 * 60_000 && gap.duration < 13 * 60_000, "no signal for 10–13 min")
        r.equal(day.stops, 6)
        r.expect(day.meters > 12_000 && day.meters < 20_000, Fmt.distance(day.meters))
        // Inaccurate indoor readings are dropped; about one second in seven indoors has no reading.
        r.expect(day.raw > 70_000 && day.raw < 80_000, "\(day.raw) readings")
        r.expect(day.points.count < day.raw, "some readings left out")
        r.expect(back.end > Sample.start(thu) + 86_400_000 - 60_000, "last stop lasts till midnight")
    }

    static func weekend(_ r: Run) {
        let day = Timeline.build(Sample.day(sun, until: Int64.max), rules)
        r.equal(day.items.count, 5, describe(day))
        guard day.items.count == 5 else { return }
        r.equal(day.items[2].kind, .stay)
        r.expect(Timeline.placeOf(day.items[2], Sample.places()) == nil, "the mall has no name yet")
        r.equal(day.items[1].mode, .vehicle)
        r.equal(day.items[3].mode, .vehicle)
    }

    static func todaySoFar(_ r: Run) {
        let day = Timeline.build(Sample.day(thu, until: at(thu, 9, 41)), rules)
        r.equal(day.items.count, 3, describe(day))
        guard day.items.count == 3 else { return }
        r.equal(day.items[2].kind, .stay)
        r.expect(day.items[2].end < at(thu, 9, 41), "nothing after now")
        r.expect(Sample.day(thu, until: Sample.start(thu)).isEmpty, "nothing before the day starts")
    }

    static func glitches(_ r: Run) {
        let t0: Int64 = 1_000_000_000_000
        var raw: [Fix] = []
        for s in 0..<600 { raw.append(Fix(t0 + Int64(s) * 1000, 3.1, 101.6, 50, 5, 0)) }
        raw.append(Fix(t0 + 600_500, 3.2, 101.6, 50, 5, 0))       // 11 km in half a second
        raw.append(Fix(t0 + 601_000, 3.1001, 101.6, 50, 500, 0))  // ±500 m
        raw.append(Fix(t0 + 5_000, 3.1, 101.6, 50, 5, 0))         // same second twice
        r.equal(Timeline.clean(raw, rules).count, 600)
        let day = Timeline.build(raw, rules)
        r.equal(day.items.count, 1)
        r.equal(day.items.first?.kind, .stay)
        r.equal(day.raw, 603)
    }

    static func shortStops(_ r: Run) {
        // Walk 1.4 m/s east for 10 minutes, stand 3 minutes, walk on 10 minutes: one trip, no stop.
        let t0: Int64 = 1_000_000_000_000
        var raw: [Fix] = []
        var lon = 101.6
        var s = 0
        while s < 600 {
            raw.append(Fix(t0 + Int64(s) * 1000, 3.1, lon, 50, 5, 1.4))
            s += 1
            lon += 1.4 / 111_000
        }
        while s < 780 {
            raw.append(Fix(t0 + Int64(s) * 1000, 3.1, lon, 50, 5, 0))
            s += 1
        }
        while s < 1380 {
            raw.append(Fix(t0 + Int64(s) * 1000, 3.1, lon, 50, 5, 1.4))
            s += 1
            lon += 1.4 / 111_000
        }
        let day = Timeline.build(raw, rules)
        r.equal(day.items.count, 1)
        guard let walk = day.items.first else { return }
        r.equal(walk.mode, .walk)
        r.expect(abs(walk.meters - 1680) < 40, Fmt.distance(walk.meters))
        r.equal(day.stops, 0)
    }

    static func distances(_ r: Run) {
        // Kuala Lumpur to Penang is about 300 km in a straight line.
        let d = Geo.distance(3.1390, 101.6869, 5.4141, 100.3288)
        r.expect(d > 290_000 && d < 300_000, "KL to Penang \(d)")
        r.close(Geo.distance(3.1, 101.6, 3.1, 101.6), 0)
        let day = Timeline.build(Sample.day(thu, until: Int64.max), rules).points
        let s = Geo.simplify(day, 0, day.count - 1, 15)
        r.expect(s.count < day.count / 20, "\(s.count) points")
        r.equal(s.first, day.first)
        r.equal(s.last, day.last)
        let b = Geo.bounds(day) ?? []
        r.expect(b.count == 4 && b[0] < 3.12 && b[2] > 3.155, "bounds \(b)")
        r.expect(Geo.bounds([]) == nil, "no bounds for nothing")
    }

    static func places(_ r: Run) {
        let places = Sample.places()
        r.equal(Place.nearest(places, 3.1291, 101.6714)?.name, "Home")
        r.expect(Place.nearest(places, 3.2, 101.6) == nil, "nowhere near")
        r.equal(Place.newId(places), "place-4")
        let back = Place.fromJson(Place.toJson(places))
        r.equal(back.count, 3)
        r.equal(back.count > 1 ? back[1].name : "", "Office")
        r.equal(back.count > 1 ? back[1].radius : 0, 150)
        r.expect(Place.fromJson(Data("not json".utf8)).isEmpty, "damaged list is empty")
    }

    static func writing(_ r: Run) {
        r.equal(Fmt.fixed(1.25, 1), "1.3")
        r.equal(Fmt.fixed(-0.45, 1), "-0.5")
        r.equal(Fmt.fixed(-0.4, 0), "0")
        r.equal(Fmt.fixed(3.129, 7), "3.1290000")
        r.equal(Fmt.fixed(.nan, 1), "")
        r.equal(Fmt.count(1_234_567), "1,234,567")
        r.equal(Fmt.count(12), "12")
        r.equal(Fmt.distance(45.4), "45 m")
        r.equal(Fmt.distance(847), "850 m")
        r.equal(Fmt.distance(996), "1.0 km")
        r.equal(Fmt.distance(12_420), "12.4 km")
        r.equal(Fmt.distance(127_600), "128 km")
        r.equal(Fmt.duration(45_000), "45 s")
        r.equal(Fmt.duration(8 * 60_000 + 59_000), "8 min")
        r.equal(Fmt.duration(65 * 60_000), "1 h 5 min")
        r.equal(Fmt.duration(12 * 3_600_000), "12 h")
        r.equal(Fmt.speed(1.25), "4.5 km/h")
        r.equal(Fmt.speed(7.5), "27 km/h")
        let t = at(thu, 8, 5) + 9000
        r.equal(Fmt.time(t, kl), "8:05 am")
        r.equal(Fmt.time(Sample.start(thu), kl), "12:00 am")
        r.equal(Fmt.time(at(thu, 12, 30), kl), "12:30 pm")
        r.equal(Fmt.clock(t, kl), "08:05:09")
        r.equal(Fmt.isoLocal(t, kl), "2026-11-26T08:05:09+08:00")
        r.equal(Fmt.isoUtc(t), "2026-11-26T00:05:09Z")
        r.equal(Fmt.day(thu), "Thu 26 Nov 2026")
        r.equal(Fmt.dayName(thu.adding(-1), thu), "Yesterday")
        r.equal(Fmt.dayName(thu, thu), "Today")
        r.equal(Fmt.bytes(4_928_307), "4.7 MB")
        r.equal(Fmt.coords(3.129, 101.6713), "3.12900, 101.67130")
    }

    static func exports(_ r: Run) {
        let f = Fix(at(thu, 8, 5) + 9000, 3.129, 101.6713, 52.25, 6, 7.5)
        r.equal(Export.csvRow(f, kl), "2026-11-26T08:05:09+08:00,3.1290000,101.6713000,52.3,6.0,27.0")
        let noAlt = Fix(at(thu, 8, 5), 3.129, 101.6713, .nan, .nan, .nan)
        r.equal(Export.csvRow(noAlt, kl), "2026-11-26T08:05:00+08:00,3.1290000,101.6713000,,,")
        let two = [noAlt, f, Fix(f.t + 3_600_000, 3.13, 101.67, 50, 5, 0)]
        let gpx = Export.gpx(two, "Jejak & me", 600_000)
        r.expect(gpx.contains("<name>Jejak &amp; me</name>"), "name is escaped")
        r.expect(gpx.contains("<trkpt lat=\"3.1290000\" lon=\"101.6713000\"><time>2026-11-26T00:05:00Z</time></trkpt>"), "point without height")
        r.expect(gpx.contains("<ele>52.3</ele>"), "height")
        r.equal(gpx.components(separatedBy: "<trkseg>").count - 1, 2, "two segments")
        let csv = Export.csv(two, kl)
        r.equal(csv.split(separator: "\n").count, 4)
        r.expect(csv.hasPrefix(Export.csvHeader + "\n"), "header first")
    }

    static func wholeNumbers(_ r: Run) {
        let f = Fix(5, 3.1290012, 101.6713005, 52.25, 6.04, 1.234)
        let back = Fix.stored(f.t, f.latE7, f.lonE7, Fix.scaled(f.alt, 10), Fix.scaled(f.acc, 10), Fix.scaled(f.speed, 100))
        r.equal(f.latE7, 31_290_012)
        r.close(back.lat, f.lat)
        r.close(back.alt, 52.3)
        r.close(back.acc, 6.0)
        r.close(back.speed, 1.23)
        r.expect(Fix.scaled(.nan, 10) == nil, "unknown stays unknown")
        r.expect(Fix.stored(1, 0, 0, nil, nil, nil).alt.isNaN, "no height")
    }

    static func store(_ r: Run) {
        let path = FileManager.default.temporaryDirectory.appendingPathComponent("jejak-check-\(UUID().uuidString).db").path
        defer {
            for p in [path, path + "-wal", path + "-shm"] { try? FileManager.default.removeItem(atPath: p) }
        }
        guard let s = try? FixStore(path: path) else {
            r.expect(false, "can't open the reading store")
            return
        }
        let raw = Sample.day(thu, until: at(thu, 9, 41))
        r.equal(s.insert(raw), raw.count, "all added")
        r.equal(s.insert(Array(raw.prefix(10))), 0, "same moments are not added twice")
        r.equal(s.count(), raw.count)
        let back = s.range(Sample.start(thu), Sample.start(thu.adding(1)))
        r.equal(back.count, raw.count)
        r.equal(back.first?.latE7, raw.first?.latE7)
        r.close(back.last?.alt ?? .nan, raw.last?.alt ?? 0)
        r.close(back.last?.speed ?? .nan, raw.last?.speed ?? 0)
        r.equal(s.last()?.t, raw.last?.t)
        r.equal(s.first()?.t, raw.first?.t)
        r.equal(s.latest(20).count, 20)
        r.expect((s.latest(20).first?.t ?? 0) > (s.latest(20).last?.t ?? 0), "newest first")
        r.equal(s.count(at(thu, 8, 0), at(thu, 9, 0)), raw.filter { $0.t >= at(thu, 8, 0) && $0.t < at(thu, 9, 0) }.count)
        // Same day, same timeline, after a trip through the database.
        let a = Timeline.build(raw, rules), b = Timeline.build(back, rules)
        r.equal(a.items.count, b.items.count, "same timeline from saved readings")
        s.insert([Fix(at(thu, 9, 50), 3.1, 101.6, .nan, .nan, .nan)])
        r.expect(s.last()?.alt.isNaN == true, "unknown height saved as unknown")
        let gone = s.deleteBefore(at(thu, 8, 0))
        r.expect(gone > 20_000, "\(gone) deleted")
        r.equal(s.count(Sample.start(thu), at(thu, 8, 0)), 0)
        r.expect(s.delete(at(thu, 8, 0), at(thu, 9, 0)) > 0, "deleted an hour")
        r.expect(s.fileSize > 0, "has a size")
        s.deleteAll()
        r.equal(s.count(), 0)
        r.expect(s.last() == nil, "empty")
    }

    // ------------------------------------------------------------------ the same as the Android app

    static func fnv(_ s: String) -> String {
        var h: UInt64 = 0xcbf29ce484222325
        for b in s.utf8 {
            h ^= UInt64(b)
            h = h &* 0x100000001b3
        }
        return String(h, radix: 16)
    }

    static func sameAsAndroid(_ r: Run) {
        guard let url = Bundle.module.url(forResource: "android-sample", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let days = json["days"] as? [[String: Any]] else {
            r.expect(false, "can't read android-sample.json")
            return
        }
        r.equal(days.count, 3, "days in the Android file")
        for want in days {
            let parts = (want["date"] as? String ?? "").split(separator: "-").compactMap { Int($0) }
            guard parts.count == 3 else {
                r.expect(false, "bad date in the Android file")
                continue
            }
            let date = Day(parts[0], parts[1], parts[2])
            let untilJ = (want["until"] as? NSNumber)?.int64Value ?? -1
            let until = untilJ < 0 ? Int64.max : untilJ
            let tag = "\(date) until \(untilJ):"
            let raw = Sample.day(date, until: until)
            let day = Timeline.build(raw, rules)
            func int(_ k: String) -> Int { (want[k] as? NSNumber)?.intValue ?? -999 }
            func num(_ k: String, _ o: [String: Any]) -> Double { (o[k] as? NSNumber)?.doubleValue ?? .nan }
            r.equal(day.raw, int("raw"), "\(tag) readings")
            r.equal(day.points.count, int("kept"), "\(tag) readings kept")
            r.equal(day.stops, int("stops"), "\(tag) stops")
            r.equal(Int(day.moving), int("moving"), "\(tag) time moving")
            r.equal(Int(day.still), int("still"), "\(tag) time still")
            r.close(day.meters, num("meters", want), "\(tag) distance")
            r.close(day.minAlt, num("minAlt", want), "\(tag) lowest")
            r.close(day.maxAlt, num("maxAlt", want), "\(tag) highest")
            r.close(day.fastest, num("fastest", want), "\(tag) fastest")
            let items = want["items"] as? [[String: Any]] ?? []
            r.equal(day.items.count, items.count, "\(tag) lines")
            for (k, (got, w)) in zip(day.items, items).enumerated() {
                let it = "\(tag) line \(k)"
                r.equal(got.kind.rawValue, w["kind"] as? String ?? "", "\(it) kind")
                r.equal(got.start, (w["start"] as? NSNumber)?.int64Value ?? 0, "\(it) start")
                r.equal(got.end, (w["end"] as? NSNumber)?.int64Value ?? 0, "\(it) end")
                r.equal(got.from, (w["from"] as? NSNumber)?.intValue ?? 0, "\(it) from")
                r.equal(got.to, (w["to"] as? NSNumber)?.intValue ?? 0, "\(it) to")
                r.equal(got.mode?.rawValue ?? "", w["mode"] as? String ?? "", "\(it) mode")
                r.equal(Timeline.placeOf(got, Sample.places())?.name ?? "", w["place"] as? String ?? "", "\(it) place")
                r.close(got.lat, num("lat", w), "\(it) lat")
                r.close(got.lon, num("lon", w), "\(it) lon")
                r.close(got.meters, num("meters", w), "\(it) meters")
                r.close(got.fast, num("fast", w), "\(it) fast")
                r.close(got.climb, num("climb", w), "\(it) climb")
                r.close(got.descent, num("descent", w), "\(it) descent")
                let text = Fmt.time(got.start, kl) + " – " + Fmt.time(got.end, kl) + " · " + Fmt.duration(got.duration)
                    + (got.kind == .move ? " · " + Fmt.distance(got.meters) + " · " + Fmt.speed(got.average) : "")
                r.equal(text, w["text"] as? String ?? "", "\(it) text")
            }
            r.equal(Geo.simplify(day.points, 0, day.points.count - 1, 15).count, int("simplified"), "\(tag) map points")
            let csv = Export.csv(raw, kl)
            let gpx = Export.gpx(day.points, "Jejak " + Fmt.date(date), rules.gap)
            r.equal(csv.utf8.count, int("csvLength"), "\(tag) CSV length")
            r.equal(fnv(csv), want["csvHash"] as? String ?? "", "\(tag) CSV contents")
            r.equal(fnv(gpx), want["gpxHash"] as? String ?? "", "\(tag) GPX contents")
            let head = csv.split(separator: "\n", maxSplits: 5, omittingEmptySubsequences: false).prefix(5).map(String.init)
            r.equal(head, want["csvHead"] as? [String] ?? [], "\(tag) first CSV lines")
        }
    }
}
