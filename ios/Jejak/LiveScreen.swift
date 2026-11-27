import JejakCore
import SwiftUI

/// Start and stop recording, and see where the phone is right now: latitude, longitude and altitude, updated
/// every second.
struct LiveScreen: View {
    @Environment(Store.self) private var store
    @Environment(Recorder.self) private var recorder
    @State private var sampleRecent: [Fix] = []
    @State private var storage = Store.Storage()
    @State private var confirmStop = false

    private var recording: Bool { store.isSample || recorder.isOn }
    private var latest: Fix? { store.isSample ? sampleRecent.first : recorder.last }
    private var recent: [Fix] { store.isSample ? sampleRecent : recorder.recent }

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                if store.isSample { SampleBanner() }
                status
                if !store.isSample { warnings }
                reading
                today
                lastReadings
            }
            .padding(16)
        }
        .background(Theme.page)
        .navigationTitle("Live")
        .inlineTitle()
        .task(id: store.revision) {
            if store.isSample { sampleRecent = await store.sampleRecent() }
            // The space used grows while recording: look again now and then.
            while !Task.isCancelled {
                storage = await store.storage()
                try? await Task.sleep(for: .seconds(30))
            }
        }
        .confirmationDialog("Stop recording?", isPresented: $confirmStop, titleVisibility: .visible) {
            Button("Stop recording", role: .destructive) { recorder.stop() }
        } message: {
            Text("Jejak stops saving where you go until you press Start again.")
        }
    }

    // ------------------------------------------------------------------ recording on or off

    private var status: some View {
        Card {
            HStack(spacing: 12) {
                ZStack {
                    Circle().fill(recording ? Theme.goodSoft : Theme.greySoft).frame(width: 44, height: 44)
                    Image(systemName: recording ? "record.circle" : "pause.circle")
                        .font(.title2)
                        .foregroundStyle(recording ? Theme.good : Theme.muted)
                        .symbolEffect(.pulse, isActive: recording)
                }
                VStack(alignment: .leading, spacing: 2) {
                    Text(recording ? "Recording" : "Not recording").font(.headline)
                    Text(statusText).font(.footnote).foregroundStyle(Theme.muted).fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 0)
            }
            if store.isSample {
                Text("Turn off sample days in Settings to record for real.")
                    .font(.footnote)
                    .foregroundStyle(Theme.muted)
                    .padding(.top, 12)
            } else if recorder.isOn {
                Button("Stop") { confirmStop = true }
                    .buttonStyle(OutlineButtonStyle(color: Theme.bad))
                    .padding(.top, 14)
            } else {
                Button {
                    recorder.start()
                } label: {
                    Label("Start recording", systemImage: "location.fill")
                }
                .buttonStyle(FilledButtonStyle())
                .padding(.top, 14)
            }
        }
    }

    private var statusText: String {
        if store.isSample { return "Sample days: saving a reading every second" }
        if !recorder.isOn { return "Press Start and Jejak saves where you are \(store.interval.everyText), also with the screen off." }
        if recorder.denied { return "Waiting for permission to use your location" }
        return "Saving a reading \(store.interval.everyText), also with the screen off"
    }

    @ViewBuilder
    private var warnings: some View {
        if !recorder.servicesOn {
            Notice(icon: "location.slash.fill", title: "Location Services are off",
                   message: "Turn them on in Settings → Privacy & Security → Location Services.", fg: Theme.bad, bg: Theme.badSoft)
        } else if recorder.denied {
            Notice(icon: "location.slash.fill", title: "Jejak can't see your location",
                   message: "Allow it in Settings → Jejak → Location, and choose Always.", fg: Theme.bad, bg: Theme.badSoft) {
                Button("Open Settings") { openAppSettings() }.font(.footnote.weight(.semibold))
            }
        } else if recorder.isOn && recorder.allowed && !recorder.always {
            Notice(icon: "exclamationmark.triangle.fill", title: "Choose “Always” to keep recording",
                   message: "Jejak records with the screen off. But if the app is closed, it can only start again by itself when location is set to Always: Settings → Jejak → Location → Always.") {
                Button("Open Settings") { openAppSettings() }.font(.footnote.weight(.semibold))
            }
        }
        if recorder.allowed && !recorder.precise {
            Notice(icon: "scope", title: "Precise Location is off",
                   message: "Readings may be a few kilometres off. Turn on Precise Location in Settings → Jejak → Location.") {
                Button("Open Settings") { openAppSettings() }.font(.footnote.weight(.semibold))
            }
        }
        if recorder.lowPower {
            Notice(icon: "battery.25", title: "Low Power Mode is on",
                   message: "iPhone may give readings less often while it saves battery.")
        }
    }

    // ------------------------------------------------------------------ where the phone is now

    private var reading: some View {
        Card {
            SectionTitle("Where you are now").padding(.top, -12)
            if let f = latest {
                BigValue(label: "Latitude", value: Fmt.fixed(f.lat, 7))
                BigValue(label: "Longitude", value: Fmt.fixed(f.lon, 7))
                BigValue(label: "Altitude", value: f.hasAlt ? Fmt.fixed(f.alt, 1) + " m" : "–")
                Divider().padding(.vertical, 8)
                HStack {
                    Stat(value: Fmt.accuracy(f.acc), label: "accuracy")
                    Stat(value: Fmt.speed(f.speed), label: "speed")
                }
                TimelineView(.periodic(from: .now, by: 1)) { _ in
                    Text(updated(f))
                        .font(.footnote)
                        .foregroundStyle(Theme.muted)
                        .monospacedDigit()
                        .frame(maxWidth: .infinity)
                        .padding(.top, 10)
                }
            } else {
                Text(recorder.isOn ? "Waiting for the first reading. Outdoors, this takes a few seconds." : "No readings yet.")
                    .font(.subheadline)
                    .foregroundStyle(Theme.muted)
                    .padding(.vertical, 8)
            }
        }
    }

    private func updated(_ f: Fix) -> String {
        let ago = max(0, store.nowMs - f.t) / 1000
        let when: String
        if ago < 60 {
            when = "\(ago) s ago"
        } else if ago < 3600 {
            when = "\(ago / 60) min ago"
        } else {
            let day = Day.of(f.t, store.zone)
            when = day == store.today ? "earlier today" : Fmt.day(day)
        }
        return "Updated " + when + " · " + Fmt.clock(f.t, store.zone)
    }

    private var today: some View {
        Card(padding: 12) {
            HStack {
                Stat(value: Fmt.count(store.isSample ? (sampleCount ?? 0) : recorder.savedToday), label: "readings today")
                Stat(value: Fmt.bytes(storage.bytes), label: "space used")
                Stat(value: store.interval == 1 ? "1 s" : store.interval == 60 ? "1 min" : "\(store.interval) s", label: "between readings")
            }
        }
    }

    @State private var sampleCount: Int?

    private var lastReadings: some View {
        Card(padding: 12) {
            SectionTitle("Last readings").padding(.top, -12)
            if recent.isEmpty {
                Text("The newest readings show here as they are saved.")
                    .font(.footnote)
                    .foregroundStyle(Theme.muted)
            } else {
                HStack {
                    Text("Time").frame(width: 64, alignment: .leading)
                    Text("Latitude").frame(maxWidth: .infinity, alignment: .leading)
                    Text("Longitude").frame(maxWidth: .infinity, alignment: .leading)
                    Text("Alt.").frame(width: 44, alignment: .trailing)
                }
                .font(.caption2.weight(.semibold))
                .foregroundStyle(Theme.muted)
                .padding(.bottom, 4)
                ForEach(Array(recent.enumerated()), id: \.offset) { _, f in
                    HStack {
                        Text(Fmt.clock(f.t, store.zone)).frame(width: 64, alignment: .leading).foregroundStyle(Theme.muted)
                        Text(Fmt.fixed(f.lat, 6)).frame(maxWidth: .infinity, alignment: .leading)
                        Text(Fmt.fixed(f.lon, 6)).frame(maxWidth: .infinity, alignment: .leading)
                        Text(f.hasAlt ? Fmt.fixed(f.alt, 0) : "–").frame(width: 44, alignment: .trailing)
                    }
                    .font(.system(.caption, design: .monospaced))
                    .padding(.vertical, 3)
                }
            }
        }
        .task(id: store.revision) {
            if store.isSample { sampleCount = await store.readings(on: store.today) }
        }
    }
}

/// A big monospaced number with a small label above it.
struct BigValue: View {
    let label: String
    let value: String

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(label).font(.caption).foregroundStyle(Theme.muted)
            Text(value)
                .font(.system(size: 30, weight: .semibold, design: .monospaced))
                .foregroundStyle(Theme.ink)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
                .contentTransition(.numericText())
        }
        .padding(.vertical, 3)
    }
}
