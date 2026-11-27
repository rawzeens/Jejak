import JejakCore
import SwiftUI

/// How often to record, how the timeline is read, the saved readings, sample days, battery tips.
struct SettingsScreen: View {
    @Environment(Store.self) private var store
    @State private var storage = Store.Storage()
    @State private var deletingDay = false
    @State private var confirmAll = false

    var body: some View {
        @Bindable var store = store
        Form {
            Section {
                Picker("Record every", selection: $store.interval) {
                    Text("1 second").tag(1)
                    Text("5 seconds").tag(5)
                    Text("10 seconds").tag(10)
                    Text("30 seconds").tag(30)
                    Text("1 minute").tag(60)
                }
            } header: {
                Text("Recording")
            } footer: {
                Text("Every second gives the most detail and uses the most battery. Every 5 or 10 seconds is gentler for all-day use.")
            }

            Section {
                Picker("Leave out readings worse than", selection: $store.maxAccuracy) {
                    ForEach([25, 50, 65, 100, 200], id: \.self) { Text("±\($0) m").tag($0) }
                }
                Picker("A stop is at least", selection: $store.minStayMinutes) {
                    ForEach([3, 5, 10, 15], id: \.self) { Text("\($0) minutes").tag($0) }
                }
            } header: {
                Text("Timeline")
            } footer: {
                Text("Indoors, readings can be far off; those are kept but not used for the timeline. Staying within 100 m for this long counts as a stop.")
            }

            Section {
                FactRow("Readings saved", Fmt.count(storage.readings))
                FactRow("Space used", Fmt.bytes(storage.bytes))
                if let first = storage.first {
                    FactRow("Since", Fmt.day(Day.of(first.t, store.zone)))
                }
                Picker("Keep readings for", selection: $store.keepDays) {
                    Text("Forever").tag(0)
                    Text("1 year").tag(365)
                    Text("90 days").tag(90)
                    Text("30 days").tag(30)
                    Text("7 days").tag(7)
                }
                Button("Delete a day…") { deletingDay = true }
                    .disabled(store.isSample)
                Button("Delete everything", role: .destructive) { confirmAll = true }
                    .disabled(store.isSample || storage.readings == 0)
            } header: {
                Text("Your readings")
            } footer: {
                Text("A day of readings every second takes about 2–3 MB.")
            }

            Section {
                Toggle("Show sample days", isOn: Binding(get: { store.isSample }, set: { store.setSample($0) }))
            } footer: {
                Text("Two weeks of made-up readings around Kuala Lumpur, to see how the timeline looks. Your own readings are kept and come back when you turn this off.")
            }

            Section("Battery and staying on") {
                Tip(icon: "xmark.app", text: "Don't swipe Jejak away in the app switcher. iPhone then stops it until you open it again, or until you have moved a few hundred metres if location is set to Always.")
                Tip(icon: "battery.25", text: "Low Power Mode can make readings come less often.")
                Tip(icon: "location.fill", text: "The blue location sign at the top of the screen shows Jejak is recording.")
            }

            Section("About") {
                Text("Jejak records where you go, every second, and shows your day as a timeline of stops and trips. Your readings are kept only on this phone. Looking up an address asks Apple Maps about that one spot.")
                    .font(.footnote)
                    .foregroundStyle(Theme.muted)
                FactRow("Version", Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.0.0")
            }
        }
        .navigationTitle("Settings")
        .inlineTitle()
        .task(id: store.revision) { storage = await store.storage() }
        .sheet(isPresented: $deletingDay) { DeleteDaySheet() }
        .confirmationDialog("Delete every reading?", isPresented: $confirmAll, titleVisibility: .visible) {
            Button("Delete everything", role: .destructive) {
                Task { await store.deleteEverything() }
            }
        } message: {
            Text("All \(Fmt.count(storage.readings)) readings are removed from this phone. This can't be undone. Your named places stay.")
        }
    }
}

struct Tip: View {
    let icon: String
    let text: String

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: icon).foregroundStyle(Theme.brand).frame(width: 22)
            Text(text).font(.footnote).foregroundStyle(Theme.ink)
        }
        .padding(.vertical, 2)
    }
}

/// Pick a day and delete its readings.
struct DeleteDaySheet: View {
    @Environment(Store.self) private var store
    @Environment(\.dismiss) private var dismiss
    @State private var date = Date()
    @State private var count: Int?
    @State private var confirm = false

    private var day: Day { Day.of(date, store.zone) }

    var body: some View {
        NavigationStack {
            Form {
                DatePicker("Day", selection: $date, in: ...Date(), displayedComponents: .date)
                    .datePickerStyle(.graphical)
                FactRow("Readings that day", count.map { Fmt.count($0) } ?? "…")
                Button("Delete this day", role: .destructive) {
                    confirm = true
                }
                .disabled((count ?? 0) == 0)
            }
            .navigationTitle("Delete a day")
            .inlineTitle()
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Close") { dismiss() } }
            }
            .task(id: day) { count = await store.readings(on: day) }
            .confirmationDialog("Delete \(Fmt.count(count ?? 0)) readings?", isPresented: $confirm, titleVisibility: .visible) {
                Button("Delete", role: .destructive) {
                    Task {
                        await store.deleteDay(day)
                        dismiss()
                    }
                }
            } message: {
                Text("Everything recorded on \(Fmt.day(day)) is removed from this phone. This can't be undone.")
            }
        }
    }
}
