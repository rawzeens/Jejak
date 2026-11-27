import CoreChecks
import Foundation

let result = Checks.runAll()
for f in result.failures { print("FAILED " + f) }
print("\(result.passed) checks passed, \(result.failures.count) failed")
exit(result.failures.isEmpty ? 0 : 1)
