import JejakCore
import SwiftUI

/// One day: a map of where the phone went, the day's numbers, and its stops and trips in order.
struct TimelineScreen: View {
    @Environment(Store.self) private var store
    @Environment(Recorder.self) private var recorder
    @Environment(Nav.self) private var nav
    @State private var loaded: Store.Loaded?
    @State private var picking = false
    @State private var exporting: ExportJob?

    private var date: Day { nav.day ?? store.today }

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                dateRow
                if store.isSample { SampleBanner() }
                if let l = loaded, l.date == date {
                    if l.raw.isEmpty {
                        Card { empty }
                    } else {
                        content(l)
                    }
                } else {
                    ProgressView("Reading the day…")
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 60)
                }
            }
            .padding(16)
        }
        .background(Theme.page)
        .navigationTitle("Timeline")
        .inlineTitle()
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Menu {
                    NavigationLink(value: Route.points(date, from: nil, to: nil, title: "Every reading")) {
                        Label("Every reading", systemImage: "list.bullet")
                    }
                    Button {
                        exporting = ExportJob(date: date, gpx: false)
                    } label: {
                        Label("Share as a spreadsheet (CSV)", systemImage: "tablecells")
                    }
                    Button {
                        exporting = ExportJob(date: date, gpx: true)
                    } label: {
                        Label("Share as a GPS track (GPX)", systemImage: "point.topleft.down.to.point.bottomright.curvepath")
                    }
                } label: {
                    Image(systemName: "ellipsis.circle")
                }
                .disabled(loaded?.raw.isEmpty ?? true)
            }
        }
        .task(id: LoadKey(date: date, revision: store.revision)) {
            loaded = await store.load(date)
        }
        .refreshable {
            loaded = await store.load(date, fresh: true)
        }
        .sheet(isPresented: $picking) { pickSheet }
        .sheet(item: $exporting) { ExportSheet(job: $0) }
    }

    // ------------------------------------------------------------------ choosing the day

    private var dateRow: some View {
        HStack(spacing: 8) {
            Button {
                nav.day = date.adding(-1)
            } label: {
                Image(systemName: "chevron.left").font(.headline).frame(width: 40, height: 40)
            }
            .accessibilityLabel("Day before")
            Button {
                picking = true
            } label: {
                VStack(spacing: 1) {
                    Text(Fmt.dayName(date, store.today)).font(.headline).foregroundStyle(Theme.ink)
                    if date >= store.today.adding(-1) {
                        Text(Fmt.day(date)).font(.caption).foregroundStyle(Theme.muted)
                    }
                }
                .frame(maxWidth: .infinity)
            }
            Button {
                nav.day = date.adding(1)
            } label: {
                Image(systemName: "chevron.right").font(.headline).frame(width: 40, height: 40)
            }
            .disabled(date >= store.today)
            .accessibilityLabel("Next day")
        }
        .padding(6)
        .background(Color.white, in: RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Theme.line))
    }

    private var pickSheet: some View {
        NavigationStack {
            DatePicker("Day", selection: Binding(
                get: { date.noon(in: store.zone) },
                set: { nav.day = Day.of($0, store.zone) }
            ), in: ...store.today.noon(in: store.zone), displayedComponents: .date)
                .datePickerStyle(.graphical)
                .padding()
                .navigationTitle("Choose a day")
                .inlineTitle()
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Today") {
                            nav.day = nil
                            picking = false
                        }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Done") { picking = false }
                    }
                }
        }
        .presentationDetents([.medium, .large])
    }

    // ------------------------------------------------------------------ the day

    private var empty: some View {
        let today = date == store.today
        let message: String
        if today && !recorder.isOn && !store.isSample {
            message = "Go to Live and press Start. Jejak then saves where you are every second, also with the screen off."
        } else if today {
            message = "Readings appear here as they come in. Pull down to see the newest."
        } else {
            message = "Jejak has no readings for this day."
        }
        return EmptyMessage(icon: "point.topleft.down.to.point.bottomright.curvepath", title: "Nothing recorded this day", message: message)
    }

    @ViewBuilder
    private func content(_ l: Store.Loaded) -> some View {
        TrackMap(lines: l.mapLines, stops: l.mapStops)
            .id("\(l.date.epochDay)-\(l.made)")
        Card(padding: 12) {
            HStack {
                Stat(value: Fmt.distance(l.day.meters), label: "travelled")
                Stat(value: Fmt.duration(l.day.moving), label: "moving")
                Stat(value: "\(l.day.stops)", label: l.day.stops == 1 ? "stop" : "stops")
                Stat(value: Fmt.count(l.day.raw), label: "readings")
            }
        }
        if l.day.items.isEmpty {
            Card {
                Text("Not enough good readings yet to make a timeline. Readings that may be more than \(store.maxAccuracy) m off are left out.")
                    .font(.subheadline)
                    .foregroundStyle(Theme.muted)
            }
        } else {
            Card(padding: 6) {
                ForEach(Array(l.day.items.enumerated()), id: \.offset) { k, item in
                    if item.kind == .gap {
                        ItemRow(item: item, number: nil, title: "No signal", zone: store.zone)
                    } else {
                        NavigationLink(value: Route.item(l.date, k)) {
                            ItemRow(item: item, number: l.stopNumber(k), title: title(item), zone: store.zone)
                        }
                        .buttonStyle(.plain)
                    }
                    if k < l.day.items.count - 1 { Divider().padding(.leading, 58) }
                }
            }
        }
    }

    private func title(_ item: Timeline.Item) -> String {
        switch item.kind {
        case .stay: return store.name(of: item)
        case .move: return (item.mode?.label ?? "Moving") + " · " + Fmt.distance(item.meters)
        case .gap: return "No signal"
        }
    }
}

struct LoadKey: Hashable {
    let date: Day
    let revision: Int
}

/// One line of the timeline: a stop, a trip or a time with no signal.
struct ItemRow: View {
    let item: Timeline.Item
    let number: Int?
    let title: String
    let zone: TimeZone
    var chevron = true

    var body: some View {
        HStack(spacing: 12) {
            icon
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(item.kind == .gap ? Theme.muted : Theme.ink)
                    .lineLimit(2)
                Text(subtitle).font(.footnote).foregroundStyle(Theme.muted).monospacedDigit()
            }
            Spacer(minLength: 4)
            if item.kind != .gap && chevron {
                Image(systemName: "chevron.right").font(.caption.weight(.semibold)).foregroundStyle(Theme.line)
            }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 10)
        .contentShape(Rectangle())
    }

    private var subtitle: String {
        var s = Fmt.time(item.start, zone) + " – " + Fmt.time(item.end, zone) + " · " + Fmt.duration(item.duration)
        if item.kind == .move { s += " · " + Fmt.speed(item.average) }
        return s
    }

    @ViewBuilder
    private var icon: some View {
        switch item.kind {
        case .stay:
            ZStack {
                Circle().fill(Theme.accent)
                if let number {
                    Text("\(number)").font(.subheadline.weight(.bold)).foregroundStyle(.white)
                } else {
                    Image(systemName: "mappin").foregroundStyle(.white)
                }
            }
            .frame(width: 36, height: 36)
        case .move:
            Image(systemName: item.mode?.symbol ?? "location.fill")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(Theme.brand)
                .frame(width: 36, height: 36)
                .background(Theme.brandSoft, in: Circle())
        case .gap:
            Image(systemName: "antenna.radiowaves.left.and.right.slash")
                .font(.subheadline)
                .foregroundStyle(Theme.muted)
                .frame(width: 36, height: 36)
                .background(Theme.greySoft, in: Circle())
        }
    }
}
