import JejakCore
import MapKit
import SwiftUI

/// A track to draw on the map.
struct MapLine: Identifiable {
    let id: Int
    let coords: [CLLocationCoordinate2D]
}

/// A numbered stop (or the start and end of a trip) on the map.
struct MapStop: Identifiable {
    let id: Int
    let label: String
    let coordinate: CLLocationCoordinate2D
    var color: Color = Theme.accent
}

/// Apple Maps with the day's trips as blue lines and its stops as numbered orange dots.
struct TrackMap: View {
    let lines: [MapLine]
    let stops: [MapStop]
    var circle: (CLLocationCoordinate2D, Double)?
    var height: CGFloat = 280

    var body: some View {
        Map(initialPosition: .region(region)) {
            ForEach(lines) { line in
                MapPolyline(coordinates: line.coords)
                    .stroke(Theme.brand, style: StrokeStyle(lineWidth: 4, lineCap: .round, lineJoin: .round))
            }
            if let circle {
                MapCircle(center: circle.0, radius: circle.1)
                    .foregroundStyle(Theme.brand.opacity(0.14))
                    .stroke(Theme.brand, lineWidth: 1.5)
            }
            ForEach(stops) { s in
                Annotation("", coordinate: s.coordinate, anchor: .center) {
                    Text(s.label)
                        .font(.caption2.weight(.bold))
                        .foregroundStyle(.white)
                        .frame(minWidth: 22, minHeight: 22)
                        .background(s.color, in: Circle())
                        .overlay(Circle().stroke(.white, lineWidth: 2))
                }
            }
        }
        .mapStyle(.standard(pointsOfInterest: .excludingAll))
        .frame(height: height)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Theme.line))
    }

    /// Fits everything drawn, with a little room around it.
    private var region: MKCoordinateRegion {
        var all: [CLLocationCoordinate2D] = lines.flatMap(\.coords) + stops.map(\.coordinate)
        if let circle { all.append(circle.0) }
        guard let first = all.first else {
            return MKCoordinateRegion(center: CLLocationCoordinate2D(latitude: 3.139, longitude: 101.6869),
                                      span: MKCoordinateSpan(latitudeDelta: 0.2, longitudeDelta: 0.2))
        }
        var a = first.latitude, b = first.longitude, c = first.latitude, d = first.longitude
        for p in all {
            a = min(a, p.latitude)
            b = min(b, p.longitude)
            c = max(c, p.latitude)
            d = max(d, p.longitude)
        }
        let minSpan = circle.map { $0.1 / 111_000 * 3 } ?? 0.006
        return MKCoordinateRegion(center: CLLocationCoordinate2D(latitude: (a + c) / 2, longitude: (b + d) / 2),
                                  span: MKCoordinateSpan(latitudeDelta: max(minSpan, (c - a) * 1.35), longitudeDelta: max(minSpan, (d - b) * 1.35)))
    }
}

extension Fix {
    var coordinate: CLLocationCoordinate2D { CLLocationCoordinate2D(latitude: lat, longitude: lon) }
}

extension Store.Loaded {
    /// Every trip of the day as a line.
    var mapLines: [MapLine] {
        tracks.keys.sorted().map { MapLine(id: $0, coords: tracks[$0]?.map(\.coordinate) ?? []) }
    }

    /// The day's stops, numbered from 1 in time order.
    var mapStops: [MapStop] {
        var n = 0
        var out: [MapStop] = []
        for (k, it) in day.items.enumerated() where it.kind == .stay {
            n += 1
            out.append(MapStop(id: k, label: "\(n)", coordinate: CLLocationCoordinate2D(latitude: it.lat, longitude: it.lon)))
        }
        return out
    }

    /// The stop number shown on the map for a line of the timeline.
    func stopNumber(_ index: Int) -> Int? {
        guard index < day.items.count, day.items[index].kind == .stay else { return nil }
        return day.items[..<index].filter { $0.kind == .stay }.count + 1
    }
}
