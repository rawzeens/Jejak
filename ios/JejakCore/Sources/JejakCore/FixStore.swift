import Foundation
import SQLite3

/// Keeps every reading on the phone in a small database. Positions and the other values are kept as whole numbers
/// (ten-millionths of a degree, tenths of a metre, centimetres per second), about 30 bytes a reading.
public final class FixStore: @unchecked Sendable {
    public struct Failure: Error, CustomStringConvertible {
        public let description: String
    }

    public let path: String
    private var db: OpaquePointer?
    private let lock = NSLock()

    public init(path: String) throws {
        self.path = path
        if sqlite3_open_v2(path, &db, SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE | SQLITE_OPEN_FULLMUTEX, nil) != SQLITE_OK {
            let message = db.map { String(cString: sqlite3_errmsg($0)) } ?? "can't open"
            sqlite3_close(db)
            db = nil
            throw Failure(description: message)
        }
        exec("PRAGMA journal_mode=WAL")
        exec("PRAGMA synchronous=NORMAL")
        exec("CREATE TABLE IF NOT EXISTS fixes (t INTEGER PRIMARY KEY, lat INTEGER NOT NULL, lon INTEGER NOT NULL, alt INTEGER, acc INTEGER, spd INTEGER)")
    }

    deinit {
        sqlite3_close(db)
    }

    /// Adds readings; ones already saved for the same moment are skipped. Returns how many were added.
    @discardableResult
    public func insert(_ fixes: [Fix]) -> Int {
        lock.lock()
        defer { lock.unlock() }
        if fixes.isEmpty { return 0 }
        exec("BEGIN")
        var stmt: OpaquePointer?
        var added = 0
        if sqlite3_prepare_v2(db, "INSERT OR IGNORE INTO fixes (t, lat, lon, alt, acc, spd) VALUES (?, ?, ?, ?, ?, ?)", -1, &stmt, nil) == SQLITE_OK {
            for f in fixes {
                sqlite3_bind_int64(stmt, 1, f.t)
                sqlite3_bind_int64(stmt, 2, Int64(f.latE7))
                sqlite3_bind_int64(stmt, 3, Int64(f.lonE7))
                bind(stmt, 4, Fix.scaled(f.alt, 10))
                bind(stmt, 5, Fix.scaled(f.acc, 10))
                bind(stmt, 6, Fix.scaled(f.speed, 100))
                if sqlite3_step(stmt) == SQLITE_DONE { added += Int(sqlite3_changes(db)) }
                sqlite3_reset(stmt)
            }
        }
        sqlite3_finalize(stmt)
        exec("COMMIT")
        return added
    }

    /// Readings from `from` (included) to `to` (not included), oldest first.
    public func range(_ from: Int64, _ to: Int64) -> [Fix] {
        query("SELECT t, lat, lon, alt, acc, spd FROM fixes WHERE t >= ? AND t < ? ORDER BY t", [from, to])
    }

    /// The newest readings, newest first.
    public func latest(_ limit: Int) -> [Fix] {
        query("SELECT t, lat, lon, alt, acc, spd FROM fixes ORDER BY t DESC LIMIT ?", [Int64(limit)])
    }

    public func last() -> Fix? {
        latest(1).first
    }

    public func first() -> Fix? {
        query("SELECT t, lat, lon, alt, acc, spd FROM fixes ORDER BY t LIMIT 1", []).first
    }

    public func count(_ from: Int64, _ to: Int64) -> Int {
        number("SELECT COUNT(*) FROM fixes WHERE t >= ? AND t < ?", [from, to])
    }

    public func count() -> Int {
        number("SELECT COUNT(*) FROM fixes", [])
    }

    @discardableResult
    public func delete(_ from: Int64, _ to: Int64) -> Int {
        change("DELETE FROM fixes WHERE t >= ? AND t < ?", [from, to])
    }

    @discardableResult
    public func deleteBefore(_ t: Int64) -> Int {
        change("DELETE FROM fixes WHERE t < ?", [t])
    }

    public func deleteAll() {
        change("DELETE FROM fixes", [])
        compact()
    }

    /// Gives the space of deleted readings back to the phone.
    public func compact() {
        lock.lock()
        defer { lock.unlock() }
        exec("PRAGMA wal_checkpoint(TRUNCATE)")
        exec("VACUUM")
    }

    /// Bytes used on the phone.
    public var fileSize: Int64 {
        var total: Int64 = 0
        for p in [path, path + "-wal", path + "-shm"] {
            if let n = (try? FileManager.default.attributesOfItem(atPath: p))?[.size] as? NSNumber { total += n.int64Value }
        }
        return total
    }

    // ------------------------------------------------------------------ helpers

    private func exec(_ sql: String) {
        sqlite3_exec(db, sql, nil, nil, nil)
    }

    private func bind(_ stmt: OpaquePointer?, _ i: Int32, _ v: Int?) {
        if let v { sqlite3_bind_int64(stmt, i, Int64(v)) } else { sqlite3_bind_null(stmt, i) }
    }

    private func prepare(_ sql: String, _ args: [Int64]) -> OpaquePointer? {
        var stmt: OpaquePointer?
        guard sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK else {
            sqlite3_finalize(stmt)
            return nil
        }
        for (k, a) in args.enumerated() { sqlite3_bind_int64(stmt, Int32(k + 1), a) }
        return stmt
    }

    private func query(_ sql: String, _ args: [Int64]) -> [Fix] {
        lock.lock()
        defer { lock.unlock() }
        guard let stmt = prepare(sql, args) else { return [] }
        defer { sqlite3_finalize(stmt) }
        var out: [Fix] = []
        func opt(_ c: Int32) -> Int? {
            sqlite3_column_type(stmt, c) == SQLITE_NULL ? nil : Int(sqlite3_column_int64(stmt, c))
        }
        while sqlite3_step(stmt) == SQLITE_ROW {
            out.append(Fix.stored(sqlite3_column_int64(stmt, 0), Int(sqlite3_column_int64(stmt, 1)), Int(sqlite3_column_int64(stmt, 2)),
                                  opt(3), opt(4), opt(5)))
        }
        return out
    }

    private func number(_ sql: String, _ args: [Int64]) -> Int {
        lock.lock()
        defer { lock.unlock() }
        guard let stmt = prepare(sql, args) else { return 0 }
        defer { sqlite3_finalize(stmt) }
        return sqlite3_step(stmt) == SQLITE_ROW ? Int(sqlite3_column_int64(stmt, 0)) : 0
    }

    @discardableResult
    private func change(_ sql: String, _ args: [Int64]) -> Int {
        lock.lock()
        defer { lock.unlock() }
        guard let stmt = prepare(sql, args) else { return 0 }
        defer { sqlite3_finalize(stmt) }
        return sqlite3_step(stmt) == SQLITE_DONE ? Int(sqlite3_changes(db)) : 0
    }
}
