import Foundation

/// A calendar date without a time, like 26 November 2026 (the same as Java's LocalDate on Android).
public struct Day: Hashable, Comparable, Sendable, CustomStringConvertible {
    public let year: Int
    public let month: Int
    public let day: Int

    public init(_ year: Int, _ month: Int, _ day: Int) {
        self.year = year
        self.month = month
        self.day = day
    }

    /// Days since 1 January 1970.
    public init(epochDay: Int) {
        let z = epochDay + 719_468
        let era = (z >= 0 ? z : z - 146_096) / 146_097
        let doe = z - era * 146_097
        let yoe = (doe - doe / 1460 + doe / 36524 - doe / 146_096) / 365
        let doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        let mp = (5 * doy + 2) / 153
        let d = doy - (153 * mp + 2) / 5 + 1
        let m = mp < 10 ? mp + 3 : mp - 9
        self.init(yoe + era * 400 + (m <= 2 ? 1 : 0), m, d)
    }

    /// Days since 1 January 1970.
    public var epochDay: Int {
        let y = month <= 2 ? year - 1 : year
        let era = (y >= 0 ? y : y - 399) / 400
        let yoe = y - era * 400
        let doy = (153 * (month + (month > 2 ? -3 : 9)) + 2) / 5 + day - 1
        let doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146_097 + doe - 719_468
    }

    /// 0 = Monday … 6 = Sunday.
    public var weekday: Int {
        ((epochDay + 3) % 7 + 7) % 7
    }

    public var isWeekend: Bool { weekday >= 5 }

    public func adding(_ days: Int) -> Day {
        Day(epochDay: epochDay + days)
    }

    public static func < (a: Day, b: Day) -> Bool {
        a.epochDay < b.epochDay
    }

    public var description: String { Fmt.date(self) }

    /// The local date of a moment (milliseconds since 1970) in a time zone.
    public static func of(_ t: Int64, _ zone: TimeZone) -> Day {
        Fmt.parts(t, zone).day
    }

    public static func today(_ zone: TimeZone) -> Day {
        of(Int64((Date().timeIntervalSince1970 * 1000).rounded(.down)), zone)
    }

    /// Midnight at the start of this day in the time zone, in milliseconds since 1970.
    public func start(in zone: TimeZone) -> Int64 {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = zone
        guard let date = cal.date(from: DateComponents(year: year, month: month, day: day)) else {
            return Int64(epochDay) * 86_400_000 - Int64(zone.secondsFromGMT()) * 1000
        }
        return Int64((date.timeIntervalSince1970 * 1000).rounded())
    }

    /// Midnight at the end of this day (the start of the next one).
    public func end(in zone: TimeZone) -> Int64 {
        adding(1).start(in: zone)
    }

    /// A Date at noon on this day, for date pickers.
    public func noon(in zone: TimeZone) -> Date {
        Date(timeIntervalSince1970: TimeInterval(start(in: zone)) / 1000 + 12 * 3600)
    }

    public static func of(_ date: Date, _ zone: TimeZone) -> Day {
        of(Int64((date.timeIntervalSince1970 * 1000).rounded(.down)), zone)
    }
}

func floorDiv(_ a: Int64, _ b: Int64) -> Int64 {
    let q = a / b
    return (a % b != 0 && (a < 0) != (b < 0)) ? q - 1 : q
}
