import Foundation

/// One GPS reading: where the phone was at one moment.
public struct Fix: Equatable, Sendable {
    /// When, in milliseconds since 1 January 1970 (UTC).
    public let t: Int64
    /// Latitude in degrees (north is positive).
    public let lat: Double
    /// Longitude in degrees (east is positive).
    public let lon: Double
    /// Height above sea level in metres, or NaN when the phone didn't give one.
    public let alt: Double
    /// How far off the reading may be, in metres, or NaN when unknown.
    public let acc: Double
    /// Speed in metres per second, or NaN when unknown.
    public let speed: Double

    public init(_ t: Int64, _ lat: Double, _ lon: Double, _ alt: Double, _ acc: Double, _ speed: Double) {
        self.t = t
        self.lat = lat
        self.lon = lon
        self.alt = alt
        self.acc = acc
        self.speed = speed
    }

    public var hasAlt: Bool { !alt.isNaN }
    public var hasAcc: Bool { !acc.isNaN }
    public var hasSpeed: Bool { !speed.isNaN }

    // The database keeps whole numbers (about half the space of decimals).

    /// Latitude in ten-millionths of a degree (about 1 cm).
    public var latE7: Int { Int((lat * 1e7 + 0.5).rounded(.down)) }
    public var lonE7: Int { Int((lon * 1e7 + 0.5).rounded(.down)) }

    /// A decimal value in tenths (or hundredths…), or nil when unknown.
    public static func scaled(_ v: Double, _ scale: Int) -> Int? {
        v.isNaN || v.isInfinite ? nil : Int((v * Double(scale) + 0.5).rounded(.down))
    }

    public static func stored(_ t: Int64, _ latE7: Int, _ lonE7: Int, _ altDm: Int?, _ accDm: Int?, _ speedCms: Int?) -> Fix {
        Fix(t, Double(latE7) / 1e7, Double(lonE7) / 1e7,
            altDm.map { Double($0) / 10.0 } ?? .nan,
            accDm.map { Double($0) / 10.0 } ?? .nan,
            speedCms.map { Double($0) / 100.0 } ?? .nan)
    }

    public static func == (a: Fix, b: Fix) -> Bool {
        a.t == b.t && a.lat.bitPattern == b.lat.bitPattern && a.lon.bitPattern == b.lon.bitPattern
            && a.alt.bitPattern == b.alt.bitPattern && a.acc.bitPattern == b.acc.bitPattern && a.speed.bitPattern == b.speed.bitPattern
    }
}
