import SwiftUI

@main
struct JejakApp: App {
    @State private var store = Store()
    @Environment(\.scenePhase) private var phase

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(store)
                .environment(store.recorder)
                .tint(Theme.brand)
        }
        .onChange(of: phase) { _, now in
            // Save the waiting readings before iPhone puts the app in the background.
            if now != .active { store.recorder.flush() }
        }
    }
}
