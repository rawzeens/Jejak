import Foundation

/// Made-up readings, one every second, for trying the app and for screenshots: weekdays go home → office → lunch →
/// office → gym → home (losing signal on the way back), weekends go to the shopping mall.
/// Uses whole-number maths only, so it makes exactly the same readings as the Android app.
public enum Sample {
    public static let zone = TimeZone(identifier: "Asia/Kuala_Lumpur")!
    /// How many days before today have sample readings.
    public static let daysBack = 13

    // [latitude, longitude] in ten-millionths of a degree, height in tenths of a metre
    static let home = [31290000, 1016713000, 520]
    static let office = [31556000, 1017146000, 610]
    static let lunch = [31530000, 1017105000, 590]
    static let gym = [31181000, 1016772000, 480]
    static let mall = [31490000, 1017134000, 640]

    // Trip legs: [latitude, longitude, height, seconds, signal (1 = yes)], each from where the last one ended.
    static let toWork: [[Int]] = [
        [31312000, 1016745000, 530, 120, 1],
        [31312000, 1016745000, 530, 90, 1],   // traffic light
        [31345000, 1016862000, 560, 150, 1],
        [31418000, 1016951000, 600, 110, 1],
        [31418000, 1016951000, 600, 180, 1],  // jam
        [31489000, 1017040000, 580, 400, 1],
        [31540000, 1017118000, 600, 120, 1],
        [office[0], office[1], office[2], 90, 1],
    ]
    static let toLunch: [[Int]] = [
        [31540000, 1017150000, 600, 140, 1],
        [lunch[0], lunch[1], lunch[2], 380, 1],
    ]
    static let fromLunch: [[Int]] = [
        [31540000, 1017150000, 600, 380, 1],
        [office[0], office[1], office[2], 140, 1],
    ]
    static let toGym: [[Int]] = [
        [31540000, 1017118000, 600, 90, 1],
        [31440000, 1017010000, 560, 130, 1],
        [31440000, 1017010000, 560, 120, 1],  // traffic light
        [31300000, 1016900000, 520, 150, 1],
        [gym[0], gym[1], gym[2], 220, 1],
    ]
    static let gymHome: [[Int]] = [
        [31230000, 1016700000, 500, 100, 1],
        [31262000, 1016690000, 510, 60, 1],
        [31330000, 1016790000, 540, 700, 0],  // no signal
        [home[0], home[1], home[2], 90, 1],
    ]
    static let toMall: [[Int]] = [
        [31312000, 1016745000, 530, 100, 1],
        [31345000, 1016862000, 560, 110, 1],
        [31418000, 1016951000, 600, 100, 1],
        [31470000, 1017080000, 620, 140, 1],
        [mall[0], mall[1], mall[2], 90, 1],
    ]
    static let fromMall: [[Int]] = [
        [31470000, 1017080000, 620, 90, 1],
        [31418000, 1016951000, 600, 140, 1],
        [31345000, 1016862000, 560, 100, 1],
        [31312000, 1016745000, 530, 110, 1],
        [home[0], home[1], home[2], 100, 1],
    ]

    /// The places named in the sample (the lunch spot and the mall are left for the person to name).
    public static func places() -> [Place] {
        [
            Place(id: "place-1", name: "Home", lat: Double(home[0]) / 1e7, lon: Double(home[1]) / 1e7, radius: 120),
            Place(id: "place-2", name: "Office", lat: Double(office[0]) / 1e7, lon: Double(office[1]) / 1e7, radius: 150),
            Place(id: "place-3", name: "Gym", lat: Double(gym[0]) / 1e7, lon: Double(gym[1]) / 1e7, radius: 100),
        ]
    }

    public static func has(_ date: Day, today: Day) -> Bool {
        !(date > today) && !(date < today.adding(-daysBack))
    }

    public static func start(_ date: Day) -> Int64 {
        date.start(in: zone)
    }

    /// The day's readings made before `until` (ms).
    public static func day(_ date: Day, until: Int64) -> [Fix] {
        var m = Maker(dayStart: start(date), until: until, seed: UInt64(bitPattern: Int64(date.epochDay) * 1_000_003 + 7))
        if date.isWeekend {
            m.stay(home, hms(10, 30))
            m.trip(toMall)
            m.stay(mall, hms(14, 10))
            m.trip(fromMall)
        } else {
            m.stay(home, hms(7, 45))
            m.trip(toWork)
            m.stay(office, hms(12, 30))
            m.trip(toLunch)
            m.stay(lunch, hms(13, 25))
            m.trip(fromLunch)
            m.stay(office, hms(18, 5))
            m.trip(toGym)
            m.stay(gym, hms(19, 45))
            m.trip(gymHome)
        }
        m.stay(home, 86_400)
        return m.out
    }

    static func hms(_ h: Int, _ m: Int) -> Int {
        h * 3600 + m * 60
    }

    /// Writes one reading per second.
    private struct Maker {
        var out: [Fix] = []
        let dayStart: Int64
        let until: Int64
        var state: UInt64
        var second = 0
        var lat = home[0], lon = home[1], alt = home[2]

        init(dayStart: Int64, until: Int64, seed: UInt64) {
            self.dayStart = dayStart
            self.until = until
            self.state = seed
            out.reserveCapacity(86_400)
        }

        mutating func next(_ bound: Int) -> Int {
            state = state &* 6364136223846793005 &+ 1442695040888963407
            return Int((state >> 33) % UInt64(bound))
        }

        mutating func jitter(_ r: Int) -> Int {
            next(2 * r + 1) - r
        }

        /// Indoors at a place until the given second of the day.
        mutating func stay(_ place: [Int], _ untilSecond: Int) {
            lat = place[0]
            lon = place[1]
            alt = place[2]
            while second < untilSecond {
                let missing = next(100), dLat = jitter(900), dLon = jitter(900), dAlt = jitter(20)
                let acc = 8 + next(23), speed = next(31), bad = next(1500)
                if missing >= 15 {
                    if bad == 0 {
                        emit(lat + 3000 + dLat, lon - 2500 + dLon, alt + dAlt, 160, Double(speed) / 100.0)
                    } else {
                        emit(lat + dLat, lon + dLon, alt + dAlt, acc, Double(speed) / 100.0)
                    }
                }
                second += 1
            }
        }

        /// Travelling along the legs, outdoors.
        mutating func trip(_ legs: [[Int]]) {
            for leg in legs {
                let fLat = lat, fLon = lon, fAlt = alt, secs = leg[3]
                let dy = Double(leg[0] - fLat) * 0.0111319491, dx = Double(leg[1] - fLon) * 0.0111319491 * 0.9985
                let v = (dx * dx + dy * dy).squareRoot() / Double(secs)
                for k in 1...secs {
                    let pLat = fLat + (leg[0] - fLat) * k / secs
                    let pLon = fLon + (leg[1] - fLon) * k / secs
                    let pAlt = fAlt + (leg[2] - fAlt) * k / secs
                    _ = next(100)
                    let dLat = jitter(200), dLon = jitter(200), dAlt = jitter(8)
                    let acc = 3 + next(6), sj = next(31)
                    _ = next(1500)
                    let speed = (max(0, v + Double(sj - 15) / 100.0) * 100 + 0.5).rounded(.down) / 100
                    if leg[4] == 1 { emit(pLat + dLat, pLon + dLon, pAlt + dAlt, acc, speed) }
                    second += 1
                }
                lat = leg[0]
                lon = leg[1]
                alt = leg[2]
            }
        }

        mutating func emit(_ latE7: Int, _ lonE7: Int, _ altDm: Int, _ acc: Int, _ speed: Double) {
            let t = dayStart + Int64(second) * 1000
            if t >= until { return }
            out.append(Fix(t, Double(latE7) / 1e7, Double(lonE7) / 1e7, Double(altDm) / 10.0, Double(acc), speed))
        }
    }
}
