import CoreLocation
import JejakCore
import SwiftUI

/// One stop or trip: its own map, the facts, and naming the place.
struct ItemScreen: View {
    let date: Day
    let index: Int
    @Environment(Store.self) private var store
    @State private var loaded: Store.Loaded?
    @State private var naming = false
    @State private var lookingUp = false
    @State private var noAddress = false

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                if let l = loaded {
                    if index < l.day.items.count {
                        content(l, l.day.items[index])
                    } else {
                        EmptyMessage(icon: "questionmark", title: "Not found", message: "This part of the day is no longer there.")
                    }
                } else {
                    ProgressView().padding(.vertical, 60)
                }
            }
            .padding(16)
        }
        .background(Theme.page)
        .navigationTitle(navTitle)
        .inlineTitle()
        .task(id: store.revision) { loaded = await store.load(date) }
    }

    private var navTitle: String {
        guard let l = loaded, index < l.day.items.count else { return "" }
        return l.day.items[index].kind == .stay ? "Stop" : "Trip"
    }

    @ViewBuilder
    private func content(_ l: Store.Loaded, _ item: Timeline.Item) -> some View {
        let zone = store.zone
        let place = Timeline.placeOf(item, store.places)
        if item.kind == .stay {
            TrackMap(lines: [], stops: [MapStop(id: 0, label: l.stopNumber(index).map(String.init) ?? "", coordinate: CLLocationCoordinate2D(latitude: item.lat, longitude: item.lon))],
                     circle: (CLLocationCoordinate2D(latitude: item.lat, longitude: item.lon), place?.radius ?? l.rules.stayRadius), height: 240)
        } else {
            let track = l.tracks[index] ?? []
            TrackMap(lines: [MapLine(id: 0, coords: track.map(\.coordinate))],
                     stops: [track.first, track.last].compactMap { $0 }.enumerated().map {
                         MapStop(id: $0.offset, label: $0.offset == 0 ? "A" : "B", coordinate: $0.element.coordinate, color: $0.offset == 0 ? Theme.good : Theme.accent)
                     }, height: 240)
        }
        Card(padding: 6) {
            ItemRow(item: item, number: l.stopNumber(index), title: heading(item), zone: zone, chevron: false)
        }
        Card {
            if item.kind == .stay {
                FactRow("Arrived", Fmt.time(item.start, zone))
                FactRow("Left", Fmt.time(item.end, zone))
                FactRow("Stayed", Fmt.duration(item.duration))
                FactRow("Readings", Fmt.count(item.count))
                FactRow("Middle of the place", Fmt.coords(item.lat, item.lon))
                if let a = store.address(item.lat, item.lon) { FactRow("Address", a) }
                if let place { FactRow("Saved place", "\(place.name), within \(Fmt.distance(place.radius))") }
            } else {
                FactRow("Started", Fmt.time(item.start, zone))
                FactRow("Ended", Fmt.time(item.end, zone))
                FactRow("Took", Fmt.duration(item.duration))
                FactRow("Distance", Fmt.distance(item.meters))
                FactRow("Average speed", Fmt.speed(item.average))
                FactRow("Top speed", Fmt.speed(item.fast))
                FactRow("Climbed", Fmt.altitude(item.climb))
                FactRow("Went down", Fmt.altitude(item.descent))
                FactRow("Readings", Fmt.count(item.count))
                FactRow("Ended at", Fmt.coords(item.lat, item.lon))
            }
        }
        VStack(spacing: 10) {
            if item.kind == .stay {
                Button {
                    naming = true
                } label: {
                    Label(place == nil ? "Name this place" : "Rename “\(place?.name ?? "")”", systemImage: "pencil")
                }
                .buttonStyle(FilledButtonStyle())
                if store.address(item.lat, item.lon) == nil {
                    Button {
                        lookingUp = true
                        Task {
                            let a = await store.lookUpAddress(item.lat, item.lon)
                            lookingUp = false
                            noAddress = a == nil
                        }
                    } label: {
                        if lookingUp {
                            ProgressView()
                        } else {
                            Label("Look up the address", systemImage: "magnifyingglass")
                        }
                    }
                    .buttonStyle(OutlineButtonStyle())
                    .disabled(lookingUp)
                }
            }
            NavigationLink(value: Route.points(date, from: item.start, to: item.end, title: item.kind == .stay ? "Readings at this stop" : "Readings on this trip")) {
                Label("See every reading", systemImage: "list.bullet")
            }
            .buttonStyle(OutlineButtonStyle())
        }
        .sheet(isPresented: $naming) {
            NameSheet(start: place?.name ?? store.address(item.lat, item.lon) ?? "") { name in
                if var p = place {
                    p.name = name
                    store.save(p)
                } else {
                    _ = store.newPlace(name, item.lat, item.lon)
                }
            }
        }
        .alert("No address found", isPresented: $noAddress) {
            Button("OK") {}
        } message: {
            Text("Apple Maps has no street address for this spot, or the phone is offline.")
        }
    }

    private func heading(_ item: Timeline.Item) -> String {
        switch item.kind {
        case .stay: return store.name(of: item)
        case .move: return (item.mode?.label ?? "Moving") + " · " + Fmt.distance(item.meters)
        case .gap: return "No signal"
        }
    }
}

/// Asks for a place's name.
struct NameSheet: View {
    let start: String
    let done: (String) -> Void
    @State private var name = ""
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Home, Office, Mum's house…", text: $name).words()
                } footer: {
                    Text("Every stop here gets this name, on every day.")
                }
            }
            .navigationTitle("Name this place")
            .inlineTitle()
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        done(name.trimmingCharacters(in: .whitespaces))
                        dismiss()
                    }
                    .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
            .onAppear { name = start }
        }
        .presentationDetents([.medium])
    }
}

/// A file being made for sharing.
struct ExportJob: Identifiable {
    let id = UUID()
    let date: Day
    let gpx: Bool
    var from: Int64?
    var to: Int64?
}

/// Makes the CSV or GPX file, then offers to share it.
struct ExportSheet: View {
    let job: ExportJob
    @Environment(Store.self) private var store
    @Environment(\.dismiss) private var dismiss
    @State private var url: URL?
    @State private var failed = false

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                Image(systemName: job.gpx ? "point.topleft.down.to.point.bottomright.curvepath" : "tablecells")
                    .font(.largeTitle)
                    .foregroundStyle(Theme.brand)
                    .frame(width: 72, height: 72)
                    .background(Theme.brandSoft, in: Circle())
                Text(job.gpx ? "GPS track (GPX)" : "Spreadsheet (CSV)").font(.headline)
                Text(job.gpx
                     ? "Opens in map and fitness apps. Has the readings the timeline uses."
                     : "Opens in Numbers, Excel or Google Sheets. One row for every reading: time, latitude, longitude, altitude, accuracy and speed.")
                    .font(.subheadline)
                    .foregroundStyle(Theme.muted)
                    .multilineTextAlignment(.center)
                if let url {
                    Text(url.lastPathComponent + " · " + size(url)).font(.footnote).foregroundStyle(Theme.muted)
                    ShareLink(item: url) {
                        Label("Share", systemImage: "square.and.arrow.up")
                    }
                    .buttonStyle(FilledButtonStyle())
                } else if failed {
                    Text("Couldn't make the file. Is the phone full?").foregroundStyle(Theme.bad)
                } else {
                    ProgressView("Making the file…")
                }
                Spacer()
            }
            .padding(24)
            .navigationTitle("Share " + shareDay)
            .inlineTitle()
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Close") { dismiss() } }
            }
        }
        .presentationDetents([.medium])
        .task {
            url = await store.exportFile(job.date, gpx: job.gpx, from: job.from, to: job.to)
            failed = url == nil
        }
    }

    private var shareDay: String {
        let n = Fmt.dayName(job.date, store.today)
        return job.date >= store.today.adding(-1) ? n.lowercased() : n
    }

    private func size(_ url: URL) -> String {
        let n = (try? FileManager.default.attributesOfItem(atPath: url.path))?[.size] as? NSNumber
        return Fmt.bytes(n?.int64Value ?? 0)
    }
}
