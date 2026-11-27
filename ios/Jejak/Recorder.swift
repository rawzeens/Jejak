import CoreLocation
import Foundation
import JejakCore
import Observation

/// Records where the phone is, every second (or the chosen interval), also with the screen off and while other
/// apps are open. Readings are saved in small batches. If iPhone closes the app, it is woken again after the phone
/// moves a few hundred metres (with "Always" location access) and carries on recording.
@MainActor
@Observable
final class Recorder: NSObject, CLLocationManagerDelegate {
    /// The person pressed Start (and not Stop since). Remembered across launches.
    private(set) var isOn: Bool
    /// The newest reading from the phone, saved or not.
    private(set) var last: Fix?
    /// The newest saved readings, newest first.
    private(set) var recent: [Fix] = []
    private(set) var savedToday = 0
    private(set) var authorization: CLAuthorizationStatus = .notDetermined
    private(set) var precise = true
    private(set) var servicesOn = true
    private(set) var lowPower = ProcessInfo.processInfo.isLowPowerModeEnabled

    /// Seconds between saved readings.
    @ObservationIgnored var interval: Double = 1
    @ObservationIgnored var zone: () -> TimeZone = { .current }
    @ObservationIgnored var onSaved: (() -> Void)?

    @ObservationIgnored private let manager = CLLocationManager()
    @ObservationIgnored private let db: FixStore?
    @ObservationIgnored private var buffer: [Fix] = []
    @ObservationIgnored private var lastFlush = Date()
    @ObservationIgnored private var lastSaved: Int64 = 0
    @ObservationIgnored private var running = false
    #if os(iOS)
    @ObservationIgnored private var session: CLBackgroundActivitySession?
    #endif

    init(db: FixStore?) {
        self.db = db
        self.isOn = UserDefaults.standard.bool(forKey: "recording")
        super.init()
        manager.delegate = self
        authorization = manager.authorizationStatus
        precise = manager.accuracyAuthorization == .fullAccuracy
        reload()
        NotificationCenter.default.addObserver(forName: .NSProcessInfoPowerStateDidChange, object: nil, queue: .main) { [weak self] _ in
            MainActor.assumeIsolated {
                self?.lowPower = ProcessInfo.processInfo.isLowPowerModeEnabled
            }
        }
        checkServices()
        // Carry on after the app was closed or the phone restarted.
        if isOn && Recorder.allowed(manager.authorizationStatus) { begin() }
    }

    /// Re-reads the newest saved readings (after deleting some).
    func reload() {
        last = db?.last()
        recent = db?.latest(20) ?? []
        lastSaved = recent.first?.t ?? 0
        countToday()
    }

    var allowed: Bool { Recorder.allowed(authorization) }

    var always: Bool { authorization == .authorizedAlways }

    var denied: Bool { authorization == .denied || authorization == .restricted }

    static func allowed(_ s: CLAuthorizationStatus) -> Bool {
        #if os(iOS)
        return s == .authorizedAlways || s == .authorizedWhenInUse
        #else
        return s == .authorizedAlways
        #endif
    }

    // ------------------------------------------------------------------ start and stop

    func start() {
        isOn = true
        UserDefaults.standard.set(true, forKey: "recording")
        checkServices()
        switch manager.authorizationStatus {
        case .notDetermined:
            // iPhone asks "While Using the App" first; "Always" is asked for once recording has started.
            #if os(iOS)
            manager.requestWhenInUseAuthorization()
            #else
            manager.requestAlwaysAuthorization()
            #endif
        case .denied, .restricted:
            break
        default:
            begin()
        }
    }

    func stop() {
        isOn = false
        UserDefaults.standard.set(false, forKey: "recording")
        manager.stopUpdatingLocation()
        manager.stopMonitoringSignificantLocationChanges()
        #if os(iOS)
        session?.invalidate()
        session = nil
        #endif
        running = false
        flush()
    }

    /// Asks iPhone for "Always" location access, so recording can start again by itself after the app is closed.
    func askForAlways() {
        UserDefaults.standard.set(true, forKey: "askedAlways")
        manager.requestAlwaysAuthorization()
    }

    private func begin() {
        manager.desiredAccuracy = kCLLocationAccuracyBest
        manager.distanceFilter = kCLDistanceFilterNone
        manager.activityType = .other
        #if os(iOS)
        manager.allowsBackgroundLocationUpdates = true
        manager.pausesLocationUpdatesAutomatically = false
        manager.showsBackgroundLocationIndicator = true
        if session == nil { session = CLBackgroundActivitySession() }
        #endif
        manager.startUpdatingLocation()
        // Wakes the app again after it was closed, once the phone has moved a little way.
        manager.startMonitoringSignificantLocationChanges()
        running = true
        #if os(iOS)
        if manager.authorizationStatus == .authorizedWhenInUse && !UserDefaults.standard.bool(forKey: "askedAlways") {
            askForAlways()
        }
        #endif
    }

    private func checkServices() {
        Task.detached {
            let on = CLLocationManager.locationServicesEnabled()
            await MainActor.run { [weak self] in self?.servicesOn = on }
        }
    }

    // ------------------------------------------------------------------ readings

    private func take(_ fixes: [Fix]) {
        guard isOn else { return }
        // GPS ticks are not perfectly even; a 0.8 s tick still counts as "a second later".
        let step = Int64(interval * 1000) - 300
        for f in fixes {
            last = f
            if f.t - lastSaved < step { continue }
            lastSaved = f.t
            buffer.append(f)
            recent.insert(f, at: 0)
            if recent.count > 20 { recent.removeLast(recent.count - 20) }
            savedToday += 1
        }
        if buffer.count >= 10 || Date().timeIntervalSince(lastFlush) >= 10 { flush() }
    }

    /// Writes the waiting readings to the phone.
    func flush() {
        lastFlush = Date()
        guard !buffer.isEmpty, let db else { return }
        let batch = buffer
        buffer.removeAll()
        db.insert(batch)
        countToday()
        onSaved?()
    }

    private func countToday() {
        guard let db else { return }
        let z = zone()
        let today = Day.today(z)
        savedToday = db.count(today.start(in: z), today.end(in: z)) + buffer.count
    }

    nonisolated static func fix(_ l: CLLocation) -> Fix? {
        guard l.horizontalAccuracy >= 0 else { return nil }
        return Fix(Int64((l.timestamp.timeIntervalSince1970 * 1000).rounded()),
                   l.coordinate.latitude, l.coordinate.longitude,
                   l.verticalAccuracy >= 0 ? l.altitude : .nan,
                   l.horizontalAccuracy,
                   l.speed >= 0 ? l.speed : .nan)
    }

    // ------------------------------------------------------------------ what the phone tells us

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        let fixes = locations.compactMap(Recorder.fix)
        Recorder.onMain { [weak self] in self?.take(fixes) }
    }

    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let status = manager.authorizationStatus
        let precise = manager.accuracyAuthorization == .fullAccuracy
        Recorder.onMain { [weak self] in
            guard let self else { return }
            self.authorization = status
            self.precise = precise
            self.checkServices()
            if self.isOn && Recorder.allowed(status) && !self.running { self.begin() }
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        // "Location unknown" passes by itself (indoors, tunnels); a refusal shows up as a change of access.
    }

    nonisolated private static func onMain(_ work: @escaping @MainActor () -> Void) {
        if Thread.isMainThread {
            MainActor.assumeIsolated { work() }
        } else {
            Task { @MainActor in work() }
        }
    }
}
