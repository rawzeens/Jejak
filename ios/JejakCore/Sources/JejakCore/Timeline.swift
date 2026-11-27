import Foundation

/// Turns a day of GPS readings into a timeline: places the phone stayed at, the trips between them, and times with
/// no signal. The same rules as the Android app, step for step.
public enum Timeline {
    public enum Kind: String, Sendable {
        case stay = "STAY", move = "MOVE", gap = "GAP"
    }

    public enum Mode: String, Sendable {
        case walk = "WALK", cycle = "CYCLE", vehicle = "VEHICLE", flight = "FLIGHT"

        public var label: String {
            switch self {
            case .walk: return "Walking"
            case .cycle: return "Cycling"
            case .vehicle: return "In a vehicle"
            case .flight: return "Flying"
            }
        }
    }

    /// How readings are read.
    public struct Rules: Sendable, Equatable {
        /// Readings that may be further off than this (metres) are left out of the timeline.
        public var maxAccuracy = 65.0
        /// Readings within this distance (metres) of each other count as the same place.
        public var stayRadius = 100.0
        /// Staying in one place at least this long (ms) makes it a stop.
        public var minStay: Int64 = 5 * 60_000
        /// No readings for longer than this (ms) while travelling is shown as "No signal".
        public var gap: Int64 = 10 * 60_000
        /// Faster than this (m/s) from the reading before is a glitch, not real movement.
        public var glitchSpeed = 340.0

        public init() {}
    }

    /// One line of the timeline.
    public struct Item: Sendable {
        public let kind: Kind
        public let start: Int64
        public let end: Int64
        /// Readings in `Day.points` that belong to this line (inclusive); -1 for a gap.
        public let from: Int
        public let to: Int
        /// Stop: the middle of the place. Trip: where it ended.
        public var lat = 0.0
        public var lon = 0.0
        /// Trip distance in metres.
        public var meters = 0.0
        /// Trip: a fast but typical speed (95th percentile, m/s).
        public var fast = 0.0
        /// Trip: metres climbed and descended.
        public var climb = 0.0
        public var descent = 0.0
        public var mode: Mode?

        init(_ kind: Kind, _ start: Int64, _ end: Int64, _ from: Int, _ to: Int) {
            self.kind = kind
            self.start = start
            self.end = end
            self.from = from
            self.to = to
        }

        public var duration: Int64 { end - start }

        /// Average speed in m/s.
        public var average: Double {
            duration <= 0 ? 0 : meters / (Double(duration) / 1000.0)
        }

        public var count: Int { from < 0 ? 0 : to - from + 1 }
    }

    /// A day read as a timeline.
    public struct Day: Sendable {
        /// Readings used, in time order (glitches and inaccurate ones left out).
        public let points: [Fix]
        /// All readings that day.
        public let raw: Int
        public let items: [Item]
        public var meters = 0.0
        public var moving: Int64 = 0
        public var still: Int64 = 0
        public var stops = 0
        public var minAlt = Double.nan
        public var maxAlt = Double.nan
        public var fastest = 0.0

        init(points: [Fix], raw: Int, items: [Item]) {
            self.points = points
            self.raw = raw
            self.items = items
        }

        public static let empty = Day(points: [], raw: 0, items: [])

        public var stopItems: [Item] { items.filter { $0.kind == .stay } }
    }

    /// Readings in time order without the inaccurate ones and glitches.
    public static func clean(_ raw: [Fix], _ r: Rules) -> [Fix] {
        var inOrder = true
        var k = 1
        while k < raw.count {
            if raw[k].t < raw[k - 1].t {
                inOrder = false
                break
            }
            k += 1
        }
        // A stable sort, like Java's: readings at the same moment keep their order.
        let sorted = inOrder ? raw : raw.enumerated().sorted {
            $0.element.t != $1.element.t ? $0.element.t < $1.element.t : $0.offset < $1.offset
        }.map(\.element)
        var out: [Fix] = []
        out.reserveCapacity(sorted.count)
        var last: Fix?
        for p in sorted {
            if p.hasAcc && p.acc > r.maxAccuracy { continue }
            if p.lat.isNaN || p.lon.isNaN || abs(p.lat) > 90 || abs(p.lon) > 180 { continue }
            if let l = last {
                if p.t == l.t { continue }
                let secs = max(1, Double(p.t - l.t) / 1000.0)
                if Geo.distance(l, p) / secs > r.glitchSpeed { continue }
            }
            out.append(p)
            last = p
        }
        return out
    }

    public static func build(_ raw: [Fix], _ r: Rules) -> Day {
        let pts = clean(raw, r)
        let n = pts.count

        // 1. Stops: runs of readings that stay within the radius for long enough.
        var found: [(Int, Int)] = []
        var i = 0
        while i < n {
            let a = pts[i]
            var j = i + 1
            while j < n && Geo.distance(a, pts[j]) <= r.stayRadius { j += 1 }
            if pts[j - 1].t - a.t >= r.minStay {
                // Carry on from the middle of the place, which is steadier than its first reading.
                let c = centre(pts, i, j - 1)
                while j < n && Geo.distance(c.0, c.1, pts[j].lat, pts[j].lon) <= r.stayRadius { j += 1 }
                found.append((i, j - 1))
                i = j
            } else {
                i += 1
            }
        }

        // 2. Leaving a place for a few minutes and coming back is still one stop.
        var stays: [(Int, Int)] = []
        for s in found {
            if let p = stays.last {
                let cp = centre(pts, p.0, p.1), cs = centre(pts, s.0, s.1)
                let away = pts[s.0].t - pts[p.1].t
                if away <= r.minStay && Geo.distance(cp.0, cp.1, cs.0, cs.1) <= r.stayRadius {
                    stays[stays.count - 1].1 = s.1
                    continue
                }
            }
            stays.append(s)
        }

        // 3. Stops, with the trips in between.
        var lines: [Item] = []
        var next = 0
        for s in stays {
            if next < s.0 { trips(pts, next, s.0 - 1, r, &lines) }
            lines.append(stay(pts, s.0, s.1))
            next = s.1 + 1
        }
        if next < n { trips(pts, next, n - 1, r, &lines) }

        // 4. Long silences between lines.
        var items: [Item] = []
        for it in lines {
            if let prev = items.last, it.start - prev.end > r.gap {
                items.append(Item(.gap, prev.end, it.start, -1, -1))
            }
            items.append(it)
        }

        var day = Day(points: pts, raw: raw.count, items: items)
        for it in items {
            if it.kind == .stay {
                day.stops += 1
                day.still += it.duration
            } else if it.kind == .move {
                day.meters += it.meters
                day.moving += it.duration
                day.fastest = max(day.fastest, it.fast)
            }
        }
        for p in pts where p.hasAlt {
            if day.minAlt.isNaN || p.alt < day.minAlt { day.minAlt = p.alt }
            if day.maxAlt.isNaN || p.alt > day.maxAlt { day.maxAlt = p.alt }
        }
        return day
    }

    /// Average position of readings `a...b`: (lat, lon).
    static func centre(_ pts: [Fix], _ a: Int, _ b: Int) -> (Double, Double) {
        var la = 0.0, lo = 0.0
        var k = a
        while k <= b {
            la += pts[k].lat
            lo += pts[k].lon
            k += 1
        }
        let c = Double(b - a + 1)
        return (la / c, lo / c)
    }

    private static func stay(_ pts: [Fix], _ a: Int, _ b: Int) -> Item {
        var it = Item(.stay, pts[a].t, pts[b].t, a, b)
        let c = centre(pts, a, b)
        it.lat = c.0
        it.lon = c.1
        return it
    }

    /// Readings `from...to` lie between stops: one trip, or several when the signal dropped for a while.
    private static func trips(_ pts: [Fix], _ from: Int, _ to: Int, _ r: Rules, _ out: inout [Item]) {
        var start = from
        var k = from + 1
        while k <= to + 1 {
            if k <= to && pts[k].t - pts[k - 1].t <= r.gap {
                k += 1
                continue
            }
            var a = start, b = k - 1
            // Join the trip to the stop before and after it, unless the signal was lost in between.
            if a == from && a > 0 && pts[a].t - pts[a - 1].t <= r.gap { a -= 1 }
            if b == to && b + 1 < pts.count && pts[b + 1].t - pts[b].t <= r.gap { b += 1 }
            let t = trip(pts, a, b)
            if t.meters >= 30 { out.append(t) }
            start = k
            k += 1
        }
    }

    private static func trip(_ pts: [Fix], _ a: Int, _ b: Int) -> Item {
        var it = Item(.move, pts[a].t, pts[b].t, a, b)
        it.lat = pts[b].lat
        it.lon = pts[b].lon
        it.meters = Geo.trackLength(pts, a, b, 8)
        it.fast = fastSpeed(pts, a, b)
        if it.fast == 0 { it.fast = it.average }
        it.mode = it.fast < 2.6 ? .walk : it.fast < 7.5 ? .cycle : it.fast < 70 ? .vehicle : .flight
        var ref = Double.nan
        var k = a
        while k <= b {
            let p = pts[k]
            k += 1
            if !p.hasAlt { continue }
            if ref.isNaN {
                ref = p.alt
            } else if p.alt - ref >= 4 {
                it.climb += p.alt - ref
                ref = p.alt
            } else if ref - p.alt >= 4 {
                it.descent += ref - p.alt
                ref = p.alt
            }
        }
        return it
    }

    /// A fast but typical speed for a trip (95th percentile, m/s): the higher of the phone's own speed readings and
    /// the speed worked out from positions 10 seconds apart (second-by-second positions wobble too much). Using both
    /// copes with phones that report a speed of 0 while moving, and with readings that have no speed at all.
    static func fastSpeed(_ pts: [Fix], _ a: Int, _ b: Int) -> Double {
        var own: [Double] = []
        own.reserveCapacity(b - a + 1)
        for k in a...b where pts[k].hasSpeed { own.append(pts[k].speed) }
        var moved: [Double] = []
        moved.reserveCapacity(b - a + 1)
        var m = a
        for k in a...b {
            if m < k { m = k }
            while m <= b && pts[m].t - pts[k].t < 10_000 { m += 1 }
            if m > b { break }
            let dt = Double(pts[m].t - pts[k].t) / 1000.0
            if dt <= 60 { moved.append(Geo.distance(pts[k], pts[m]) / dt) }
        }
        return max(percentile95(&own), percentile95(&moved))
    }

    private static func percentile95(_ v: inout [Double]) -> Double {
        if v.isEmpty { return 0 }
        v.sort()
        return v[Int((Double(v.count - 1) * 0.95).rounded(.down))]
    }

    /// The saved place a stop is at, if any.
    public static func placeOf(_ stop: Item, _ places: [Place]) -> Place? {
        stop.kind == .stay ? Place.nearest(places, stop.lat, stop.lon) : nil
    }
}
