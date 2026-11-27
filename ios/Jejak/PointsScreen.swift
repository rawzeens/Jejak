import JejakCore
import SwiftUI

/// Every reading of a day (or of one stop or trip): time, latitude, longitude, altitude, accuracy and speed.
struct PointsScreen: View {
    let date: Day
    let from: Int64?
    let to: Int64?
    let title: String
    @Environment(Store.self) private var store
    @State private var points: [Fix] = []
    @State private var hours: [HourMark] = []
    @State private var ready = false
    @State private var exporting: ExportJob?

    var body: some View {
        let limit = Double(store.maxAccuracy)
        let zone = store.zone
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 0, pinnedViews: [.sectionHeaders]) {
                    Section {
                        if ready && points.isEmpty {
                            EmptyMessage(icon: "list.bullet", title: "No readings", message: "Nothing was recorded at this time.")
                        }
                        ForEach(points.indices, id: \.self) { k in
                            PointRow(fix: points[k], zone: zone, leftOut: points[k].hasAcc && points[k].acc > limit)
                                .id(k)
                            Divider()
                        }
                    } header: {
                        header(proxy)
                    }
                }
            }
            .background(Theme.page)
        }
        .navigationTitle(title)
        .inlineTitle()
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Menu {
                    Button {
                        exporting = ExportJob(date: date, gpx: false, from: from, to: to)
                    } label: {
                        Label("Share as a spreadsheet (CSV)", systemImage: "tablecells")
                    }
                    Button {
                        exporting = ExportJob(date: date, gpx: true, from: from, to: to)
                    } label: {
                        Label("Share as a GPS track (GPX)", systemImage: "point.topleft.down.to.point.bottomright.curvepath")
                    }
                } label: {
                    Image(systemName: "square.and.arrow.up")
                }
                .disabled(points.isEmpty)
            }
        }
        .sheet(item: $exporting) { ExportSheet(job: $0) }
        .task(id: store.revision) {
            let l = await store.load(date)
            let from = from, to = to, zone = store.zone
            let picked = await Task.detached { () -> ([Fix], [(Int, Int)]) in
                let pts = from == nil ? l.raw : l.raw.filter { $0.t >= from! && $0.t <= (to ?? Int64.max) }
                var hours: [(Int, Int)] = []
                for (k, p) in pts.enumerated() {
                    let h = Fmt.parts(p.t, zone).h
                    if hours.last?.0 != h { hours.append((h, k)) }
                }
                return (pts, hours)
            }.value
            points = picked.0
            hours = picked.1.map { HourMark(hour: $0.0, index: $0.1) }
            ready = true
        }
    }

    private func header(_ proxy: ScrollViewProxy) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(ready ? "\(Fmt.count(points.count)) readings · \(Fmt.dayName(date, store.today))" : "Reading…")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(Theme.ink)
                Spacer()
                Text("Grey: left out of the timeline (±\(store.maxAccuracy) m or worse)")
                    .font(.caption2)
                    .foregroundStyle(Theme.muted)
                    .multilineTextAlignment(.trailing)
            }
            if hours.count > 1 {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ForEach(hours) { h in
                            Button {
                                withAnimation { proxy.scrollTo(h.index, anchor: .top) }
                            } label: {
                                Text(hourText(h.hour))
                                    .font(.caption.weight(.semibold))
                                    .padding(.horizontal, 10)
                                    .padding(.vertical, 5)
                                    .background(Theme.brandSoft, in: Capsule())
                                    .foregroundStyle(Theme.brandDark)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(Theme.page)
    }

    private func hourText(_ h: Int) -> String {
        let h12 = h % 12 == 0 ? 12 : h % 12
        return "\(h12) \(h < 12 ? "am" : "pm")"
    }
}

struct HourMark: Identifiable {
    let hour: Int
    let index: Int
    var id: Int { index }
}

/// One reading: "08:05:09   3.1290115, 101.6713179" over "52 m · ±6 m · 12 km/h".
struct PointRow: View {
    let fix: Fix
    let zone: TimeZone
    let leftOut: Bool

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 10) {
            Text(Fmt.clock(fix.t, zone))
                .foregroundStyle(Theme.muted)
            VStack(alignment: .leading, spacing: 2) {
                Text(Fmt.fixed(fix.lat, 7) + ", " + Fmt.fixed(fix.lon, 7))
                    .foregroundStyle(Theme.ink)
                Text(Fmt.altitude(fix.alt) + " · " + Fmt.accuracy(fix.acc) + " · " + Fmt.speed(fix.speed))
                    .foregroundStyle(Theme.muted)
            }
            Spacer(minLength: 0)
            if leftOut { Pill(text: "left out", fg: Theme.muted, bg: Theme.greySoft) }
        }
        .font(.system(.footnote, design: .monospaced))
        .opacity(leftOut ? 0.5 : 1)
        .padding(.horizontal, 16)
        .padding(.vertical, 7)
        .background(Color.white)
    }
}
