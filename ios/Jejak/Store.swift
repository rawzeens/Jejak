import CoreLocation
import Foundation
import JejakCore
import Observation

/// The app's settings, named places and saved readings, and the recorder. Days are read in the background and
/// kept for a moment so going back and forth is quick.
@MainActor
@Observable
final class Store {
    /// A day read from the phone (or made up, for sample days) and turned into a timeline.
    struct Loaded: Sendable {
        let date: Day
        let raw: [Fix]
        let day: Timeline.Day
        /// Shortened tracks to draw, by timeline line.
        let tracks: [Int: [Fix]]
        let made: Int64
        let rules: Timeline.Rules
        let sample: Bool
    }

    private let defaults = UserDefaults.standard
    let db: FixStore?
    let recorder: Recorder
    /// Goes up after every change so screens re-read their day.
    private(set) var revision = 0
    private(set) var isSample: Bool
    /// "-sampleTime 21:30" makes sample days as they would be at that time (for screenshots).
    @ObservationIgnored private var sampleClock: (h: Int, m: Int)?
    @ObservationIgnored private var cache: [Day: Loaded] = [:]
    @ObservationIgnored private var cacheOrder: [Day] = []
    private var ownPlaces: [Place]
    private var samplePlaces = Sample.places()
    private(set) var addresses: [String: String]

    /// Seconds between saved readings.
    var interval: Int {
        didSet {
            defaults.set(interval, forKey: "interval")
            recorder.interval = Double(interval)
        }
    }

    /// Readings that may be further off than this (metres) are left out of the timeline.
    var maxAccuracy: Int {
        didSet {
            defaults.set(maxAccuracy, forKey: "maxAccuracy")
            changed()
        }
    }

    /// Minutes in one place that make a stop.
    var minStayMinutes: Int {
        didSet {
            defaults.set(minStayMinutes, forKey: "minStay")
            changed()
        }
    }

    /// Days to keep readings for; 0 keeps them forever.
    var keepDays: Int {
        didSet {
            defaults.set(keepDays, forKey: "keepDays")
            applyKeep()
        }
    }

    init() {
        let folder = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("Jejak", isDirectory: true)
        try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        let store = try? FixStore(path: folder.appendingPathComponent("readings.db").path)
        db = store
        recorder = Recorder(db: store)
        let d = UserDefaults.standard
        interval = d.object(forKey: "interval") as? Int ?? 1
        maxAccuracy = d.object(forKey: "maxAccuracy") as? Int ?? 65
        minStayMinutes = d.object(forKey: "minStay") as? Int ?? 5
        keepDays = d.object(forKey: "keepDays") as? Int ?? 0
        ownPlaces = Place.fromJson(d.data(forKey: "places"))
        addresses = d.dictionary(forKey: "addresses") as? [String: String] ?? [:]
        isSample = d.bool(forKey: "sample")
        let args = ProcessInfo.processInfo.arguments
        if args.contains("-sample") { isSample = true }
        if let i = args.firstIndex(of: "-sampleTime"), i + 1 < args.count {
            let hm = args[i + 1].split(separator: ":").compactMap { Int($0) }
            if hm.count == 2 { sampleClock = (hm[0], hm[1]) }
        }
        recorder.interval = Double(interval)
        recorder.zone = { [weak self] in self?.zone ?? .current }
        recorder.onSaved = { [weak self] in self?.savedNew() }
        applyKeep()
    }

    // ------------------------------------------------------------------ time

    /// Sample days are in Malaysia time, real ones in the phone's time zone.
    var zone: TimeZone { isSample ? Sample.zone : .current }

    var nowMs: Int64 {
        if isSample, let c = sampleClock {
            return Day.today(Sample.zone).start(in: Sample.zone) + Int64(c.h * 3600 + c.m * 60) * 1000
        }
        return Int64((Date().timeIntervalSince1970 * 1000).rounded(.down))
    }

    var today: Day { Day.of(nowMs, zone) }

    var rules: Timeline.Rules {
        var r = Timeline.Rules()
        r.maxAccuracy = Double(maxAccuracy)
        r.minStay = Int64(minStayMinutes) * 60_000
        return r
    }

    // ------------------------------------------------------------------ days

    /// The day's readings and timeline, read in the background.
    func load(_ date: Day, fresh: Bool = false) async -> Loaded {
        let rules = rules, sample = isSample, zone = zone, now = nowMs, db = db, today = today
        if !fresh, let c = cache[date], c.rules == rules, c.sample == sample, !(date == today && !sample && now - c.made > 20_000) {
            return c
        }
        if date == today && !sample { recorder.flush() }
        let start = date.start(in: zone), end = date.end(in: zone)
        let loaded = await Task.detached(priority: .userInitiated) { () -> Loaded in
            let raw: [Fix]
            if sample {
                raw = Sample.has(date, today: today) ? Sample.day(date, until: now) : []
            } else {
                raw = db?.range(start, end) ?? []
            }
            let day = Timeline.build(raw, rules)
            var tracks: [Int: [Fix]] = [:]
            for (k, it) in day.items.enumerated() where it.kind == .move {
                tracks[k] = Geo.simplify(day.points, it.from, it.to, 8)
            }
            return Loaded(date: date, raw: raw, day: day, tracks: tracks, made: now, rules: rules, sample: sample)
        }.value
        cache[date] = loaded
        cacheOrder.removeAll { $0 == date }
        cacheOrder.append(date)
        while cacheOrder.count > 3 { cache[cacheOrder.removeFirst()] = nil }
        return loaded
    }

    func changed() {
        cache.removeAll()
        cacheOrder.removeAll()
        revision += 1
    }

    private func savedNew() {
        // Today's timeline is re-read when next shown; other days haven't changed.
        if let c = cache[today], !c.sample { cache[today] = nil }
    }

    func setSample(_ on: Bool) {
        isSample = on
        defaults.set(on, forKey: "sample")
        changed()
    }

    // ------------------------------------------------------------------ the latest reading (for Live)

    /// Sample days pretend to be recording: the newest made-up readings of today.
    func sampleRecent() async -> [Fix] {
        let l = await load(today)
        return Array(l.raw.suffix(20).reversed())
    }

    var latestFix: Fix? { recorder.last }

    // ------------------------------------------------------------------ places

    var places: [Place] { isSample ? samplePlaces : ownPlaces }

    func save(_ place: Place) {
        var list = places
        if let i = list.firstIndex(where: { $0.id == place.id }) {
            list[i] = place
        } else {
            list.append(place)
        }
        setPlaces(list)
    }

    func deletePlace(_ id: String) {
        setPlaces(places.filter { $0.id != id })
    }

    func newPlace(_ name: String, _ lat: Double, _ lon: Double, radius: Double = 100) -> Place {
        let p = Place(id: Place.newId(places), name: name, lat: lat, lon: lon, radius: radius)
        save(p)
        return p
    }

    private func setPlaces(_ list: [Place]) {
        if isSample {
            samplePlaces = list
        } else {
            ownPlaces = list
            defaults.set(Place.toJson(list), forKey: "places")
        }
    }

    /// What to call a stop: its place's name, the street address if looked up, or "Unnamed place".
    func name(of stop: Timeline.Item) -> String {
        if let p = Timeline.placeOf(stop, places) { return p.name }
        return address(stop.lat, stop.lon) ?? "Unnamed place"
    }

    // ------------------------------------------------------------------ street addresses

    private func key(_ lat: Double, _ lon: Double) -> String {
        Fmt.fixed(lat, 4) + "," + Fmt.fixed(lon, 4)
    }

    func address(_ lat: Double, _ lon: Double) -> String? {
        addresses[key(lat, lon)]
    }

    /// Asks Apple Maps for the street address of a point (needs the internet). Returns nil when none is found.
    func lookUpAddress(_ lat: Double, _ lon: Double) async -> String? {
        if let a = address(lat, lon) { return a }
        let marks = try? await CLGeocoder().reverseGeocodeLocation(CLLocation(latitude: lat, longitude: lon))
        guard let m = marks?.first else { return nil }
        var parts: [String] = []
        for p in [m.name, m.subLocality, m.locality] {
            if let p, !p.isEmpty, !parts.contains(p) { parts.append(p) }
        }
        guard !parts.isEmpty else { return nil }
        let text = parts.prefix(2).joined(separator: ", ")
        addresses[key(lat, lon)] = text
        defaults.set(addresses, forKey: "addresses")
        return text
    }

    // ------------------------------------------------------------------ saved readings

    struct Storage: Sendable {
        var readings = 0
        var bytes: Int64 = 0
        var first: Fix?
    }

    func storage() async -> Storage {
        guard let db else { return Storage() }
        return await Task.detached { Storage(readings: db.count(), bytes: db.fileSize, first: db.first()) }.value
    }

    func readings(on date: Day) async -> Int {
        if isSample { return await load(date).raw.count }
        guard let db else { return 0 }
        let start = date.start(in: zone), end = date.end(in: zone)
        return await Task.detached { db.count(start, end) }.value
    }

    func deleteDay(_ date: Day) async {
        guard let db, !isSample else { return }
        recorder.flush()
        let start = date.start(in: zone), end = date.end(in: zone)
        await Task.detached {
            db.delete(start, end)
            db.compact()
        }.value
        recorder.reload()
        changed()
    }

    func deleteEverything() async {
        guard let db, !isSample else { return }
        recorder.flush()
        await Task.detached { db.deleteAll() }.value
        recorder.reload()
        changed()
    }

    /// Removes readings older than the "Keep readings for" setting.
    func applyKeep() {
        guard keepDays > 0, let db else { return }
        let cutoff = Day.today(.current).adding(-keepDays).start(in: .current)
        Task.detached(priority: .background) {
            if db.deleteBefore(cutoff) > 0 { db.compact() }
        }
        changed()
    }

    // ------------------------------------------------------------------ exports

    /// Writes a CSV or GPX file of the day's readings (or of part of it) for sharing.
    func exportFile(_ date: Day, gpx: Bool, from: Int64? = nil, to: Int64? = nil) async -> URL? {
        let l = await load(date)
        let zone = zone, gap = l.rules.gap
        let suffix = from == nil ? "" : "-" + Fmt.clock(from ?? 0, zone).replacingOccurrences(of: ":", with: "")
        let name = "Jejak-\(Fmt.date(date))\(suffix).\(gpx ? "gpx" : "csv")"
        return await Task.detached { () -> URL? in
            // The spreadsheet has every reading; the GPS track only the ones the timeline uses.
            var pts = gpx ? l.day.points : l.raw
            if let from, let to { pts = pts.filter { $0.t >= from && $0.t <= to } }
            let text = gpx ? Export.gpx(pts, "Jejak " + Fmt.date(date), gap) : Export.csv(pts, zone)
            let dir = FileManager.default.temporaryDirectory.appendingPathComponent("export", isDirectory: true)
            try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            let url = dir.appendingPathComponent(name)
            do {
                try Data(text.utf8).write(to: url, options: .atomic)
                return url
            } catch {
                return nil
            }
        }.value
    }
}

extension Int {
    /// "every second", "every 5 seconds", "every minute"
    var everyText: String {
        switch self {
        case 1: return "every second"
        case 60: return "every minute"
        default: return self % 60 == 0 ? "every \(self / 60) minutes" : "every \(self) seconds"
        }
    }
}
