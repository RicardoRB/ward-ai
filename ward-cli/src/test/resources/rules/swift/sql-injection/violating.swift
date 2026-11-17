import Foundation
import SQLite3

func find(_ url: URL, db: OpaquePointer?) {
  let name = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems?.first?.value ?? ""
  var statement: OpaquePointer?
  let sql = "SELECT * FROM users WHERE name = '" + name + "'"
  sqlite3_prepare_v2(db, sql, -1, &statement, nil)
}

func findInterpolated(_ req: Request, db: OpaquePointer?) {
  let city = req.query.get("city") ?? ""
  var statement: OpaquePointer?
  sqlite3_prepare_v2(db, "SELECT id FROM places WHERE city = '\(city)'", -1, &statement, nil)
}

func removeOld(_ url: URL, db: OpaquePointer?) {
  let table = url.query ?? ""
  sqlite3_exec(db, "DELETE FROM " + table, nil, nil, nil)
}

func search(_ url: URL, database: FMDatabase) throws {
  let term = URLComponents(url: url, resolvingAgainstBaseURL: false)?.query ?? ""
  _ = try database.executeQuery("SELECT * FROM notes WHERE body LIKE '%" + term + "%'", values: nil)
}
