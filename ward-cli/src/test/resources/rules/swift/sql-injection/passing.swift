import Foundation
import SQLite3

func findBound(_ url: URL, db: OpaquePointer?) {
  let name = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems?.first?.value ?? ""
  var statement: OpaquePointer?
  sqlite3_prepare_v2(db, "SELECT * FROM users WHERE name = ?", -1, &statement, nil)
  sqlite3_bind_text(statement, 1, name, -1, nil)
}

func findById(_ url: URL, db: OpaquePointer?) {
  let raw = url.query ?? ""
  let id = Int(raw) ?? 0
  var statement: OpaquePointer?
  sqlite3_prepare_v2(db, "SELECT * FROM users WHERE id = \(id)", -1, &statement, nil)
}

func findFixed(db: OpaquePointer?) {
  var statement: OpaquePointer?
  let sql = "SELECT * FROM users"
  sqlite3_prepare_v2(db, sql, -1, &statement, nil)
}

func searchBound(_ url: URL, database: FMDatabase) throws {
  let term = url.query ?? ""
  _ = try database.executeQuery("SELECT * FROM notes WHERE body LIKE ?", values: [term])
}
