// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "JejakCore",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "JejakCore", targets: ["JejakCore"]),
    ],
    targets: [
        // Readings, the timeline (stops, trips, no signal), exports and the reading store (same rules as the Android app).
        .target(name: "JejakCore", linkerSettings: [.linkedLibrary("sqlite3")]),
        // The checks, shared by the test suite and a small command-line runner.
        .target(name: "CoreChecks", dependencies: ["JejakCore"], resources: [.copy("Resources/android-sample.json")]),
        .executableTarget(name: "core-check", dependencies: ["CoreChecks"]),
        .testTarget(name: "JejakCoreTests", dependencies: ["CoreChecks"]),
    ]
)
