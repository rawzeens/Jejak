import JejakCore
import SwiftUI

/// Pages that can be opened from the timeline.
enum Route: Hashable {
    /// One line of a day's timeline.
    case item(Day, Int)
    /// Every reading of a day, or of part of it.
    case points(Day, from: Int64?, to: Int64?, title: String)
}

@MainActor
@Observable
final class Nav {
    var tab = 0
    var paths: [[Route]] = [[], [], [], []]
    /// The day the timeline shows (nil: today).
    var day: Day?
}

/// Timeline, Live, Places and Settings along the bottom.
struct RootView: View {
    @Environment(Store.self) private var store
    @State private var nav = Nav()

    var body: some View {
        @Bindable var nav = nav
        TabView(selection: $nav.tab) {
            stack(0) { TimelineScreen() }
                .tabItem { Label("Timeline", systemImage: "point.topleft.down.to.point.bottomright.curvepath.fill") }.tag(0)
            stack(1) { LiveScreen() }
                .tabItem { Label("Live", systemImage: "location.fill") }.tag(1)
            stack(2) { PlacesScreen() }
                .tabItem { Label("Places", systemImage: "mappin.and.ellipse") }.tag(2)
            stack(3) { SettingsScreen() }
                .tabItem { Label("Settings", systemImage: "gearshape.fill") }.tag(3)
        }
        .environment(nav)
        .onAppear(perform: openFromLaunchArguments)
    }

    private func stack<V: View>(_ i: Int, @ViewBuilder root: () -> V) -> some View {
        @Bindable var nav = nav
        return NavigationStack(path: $nav.paths[i]) {
            root()
                .navigationDestination(for: Route.self) { route in
                    switch route {
                    case .item(let day, let index): ItemScreen(date: day, index: index)
                    case .points(let day, let from, let to, let title): PointsScreen(date: day, from: from, to: to, title: title)
                    }
                }
        }
    }

    /// For screenshots: "-screen timeline", "-screen timeline:-1" (yesterday), "-screen item:2" (third line of
    /// today), "-screen item:-1:2" (of yesterday), "-screen points", "-screen points:-1", "-screen live",
    /// "-screen places", "-screen settings".
    private func openFromLaunchArguments() {
        let args = ProcessInfo.processInfo.arguments
        guard let i = args.firstIndex(of: "-screen"), i + 1 < args.count else { return }
        let parts = args[i + 1].split(separator: ":").map(String.init)
        let numbers = parts.dropFirst().compactMap { Int($0) }
        let today = store.today
        switch parts[0] {
        case "timeline":
            nav.tab = 0
            if let off = numbers.first { nav.day = today.adding(off) }
        case "item":
            nav.tab = 0
            let off = numbers.count > 1 ? numbers[0] : 0
            let index = numbers.last ?? 0
            nav.day = today.adding(off)
            nav.paths[0] = [.item(today.adding(off), index)]
        case "points":
            nav.tab = 0
            let day = today.adding(numbers.first ?? 0)
            nav.day = day
            nav.paths[0] = [.points(day, from: nil, to: nil, title: "Every reading")]
        case "live": nav.tab = 1
        case "places": nav.tab = 2
        case "settings": nav.tab = 3
        default: break
        }
    }
}
