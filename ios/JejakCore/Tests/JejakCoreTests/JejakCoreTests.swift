import CoreChecks
import XCTest

final class JejakCoreTests: XCTestCase {
    func testTimelineExportsAndStoreWorkLikeTheAndroidApp() {
        let result = Checks.runAll()
        XCTAssertTrue(result.failures.isEmpty, result.failures.joined(separator: "\n"))
        XCTAssertGreaterThan(result.passed, 100)
    }
}
