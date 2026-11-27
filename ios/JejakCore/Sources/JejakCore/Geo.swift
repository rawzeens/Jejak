import Foundation

/// Distances on the Earth and shortening a long track for drawing.
public enum Geo {
    /// Mean radius of the Earth in metres.
    public static let earth = 6371008.8
    /// Metres in one degree of latitude.
    static let degree = earth * Double.pi / 180
    /// The same number Java's Math.toRadians uses.
    static let toRadians = 0.017453292519943295

    /// Straight-line distance in metres between two points (haversine).
    public static func distance(_ lat1: Double, _ lon1: Double, _ lat2: Double, _ lon2: Double) -> Double {
        let p1 = lat1 * toRadians, p2 = lat2 * toRadians
        let dp = p2 - p1, dl = (lon2 - lon1) * toRadians
        let a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * earth * asin(min(1, a.squareRoot()))
    }

    public static func distance(_ a: Fix, _ b: Fix) -> Double {
        distance(a.lat, a.lon, b.lat, b.lon)
    }

    /// Length of the track from point `from` to `to` (inclusive). Small wobbles are left out: a step only counts
    /// once the phone is at least `step` metres from where the last counted step ended.
    public static func trackLength(_ pts: [Fix], _ from: Int, _ to: Int, _ step: Double) -> Double {
        if from < 0 || to >= pts.count || to <= from { return 0 }
        var total = 0.0
        var ref = pts[from]
        var k = from + 1
        while k <= to {
            let p = pts[k]
            let d = distance(ref, p)
            if d >= step {
                total += d
                ref = p
            }
            k += 1
        }
        total += distance(ref, pts[to])
        return total
    }

    /// Keeps only the points needed to draw the track within `tolerance` metres (Douglas–Peucker).
    /// A day of readings every second becomes a few hundred points that look the same on a map.
    public static func simplify(_ pts: [Fix], _ from: Int, _ to: Int, _ tolerance: Double) -> [Fix] {
        var out: [Fix] = []
        if from < 0 || to >= pts.count || to < from { return out }
        if to - from < 2 {
            for k in from...to { out.append(pts[k]) }
            return out
        }
        let o = pts[from]
        let kx = degree * cos(o.lat * toRadians), ky = degree
        let n = to - from + 1
        var x = [Double](repeating: 0, count: n), y = [Double](repeating: 0, count: n)
        for k in 0..<n {
            let p = pts[from + k]
            x[k] = (p.lon - o.lon) * kx
            y[k] = (p.lat - o.lat) * ky
        }
        var keep = [Bool](repeating: false, count: n)
        keep[0] = true
        keep[n - 1] = true
        var stack = [Int](repeating: 0, count: 2 * n)
        var top = 0
        stack[top] = 0; top += 1
        stack[top] = n - 1; top += 1
        let tol2 = tolerance * tolerance
        while top > 0 {
            top -= 1
            let b = stack[top]
            top -= 1
            let a = stack[top]
            var best = -1.0
            var at = -1
            let dx = x[b] - x[a], dy = y[b] - y[a], len2 = dx * dx + dy * dy
            var k = a + 1
            while k < b {
                var d2: Double
                if len2 == 0 {
                    let ex = x[k] - x[a], ey = y[k] - y[a]
                    d2 = ex * ex + ey * ey
                } else {
                    var u = ((x[k] - x[a]) * dx + (y[k] - y[a]) * dy) / len2
                    if u < 0 { u = 0 } else if u > 1 { u = 1 }
                    let ex = x[a] + u * dx - x[k], ey = y[a] + u * dy - y[k]
                    d2 = ex * ex + ey * ey
                }
                if d2 > best {
                    best = d2
                    at = k
                }
                k += 1
            }
            if at >= 0 && best > tol2 {
                keep[at] = true
                stack[top] = a; top += 1
                stack[top] = at; top += 1
                stack[top] = at; top += 1
                stack[top] = b; top += 1
            }
        }
        for k in 0..<n where keep[k] { out.append(pts[from + k]) }
        return out
    }

    /// South-west and north-east corners around the points: [minLat, minLon, maxLat, maxLon].
    public static func bounds(_ pts: [Fix]) -> [Double]? {
        if pts.isEmpty { return nil }
        var a = 90.0, b = 180.0, c = -90.0, d = -180.0
        for p in pts {
            a = min(a, p.lat)
            b = min(b, p.lon)
            c = max(c, p.lat)
            d = max(d, p.lon)
        }
        return [a, b, c, d]
    }
}
