import Foundation

/// A place the person has named, such as Home or Office. Stops inside its circle get its name.
public struct Place: Codable, Hashable, Identifiable, Sendable {
    public var id: String
    public var name: String
    public var lat: Double
    public var lon: Double
    /// Metres.
    public var radius: Double

    public init(id: String, name: String, lat: Double, lon: Double, radius: Double = 100) {
        self.id = id
        self.name = name
        self.lat = lat
        self.lon = lon
        self.radius = radius
    }

    /// The closest place whose circle holds the point, or nil.
    public static func nearest(_ places: [Place], _ lat: Double, _ lon: Double) -> Place? {
        var best: Place?
        var bestD = Double.greatestFiniteMagnitude
        for p in places {
            let d = Geo.distance(lat, lon, p.lat, p.lon)
            if d <= p.radius && d < bestD {
                best = p
                bestD = d
            }
        }
        return best
    }

    public static func newId(_ places: [Place]) -> String {
        var n = 1
        for p in places where p.id.hasPrefix("place-") {
            if let k = Int(p.id.dropFirst(6)) { n = max(n, k + 1) }
        }
        return "place-\(n)"
    }

    public static func toJson(_ places: [Place]) -> Data {
        (try? JSONEncoder().encode(places)) ?? Data("[]".utf8)
    }

    /// A damaged list is treated as empty rather than stopping the app.
    public static func fromJson(_ data: Data?) -> [Place] {
        guard let data else { return [] }
        return (try? JSONDecoder().decode([Place].self, from: data)) ?? []
    }
}
