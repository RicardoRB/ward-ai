import XCTest

final class QueryTests: XCTestCase {
  var app: Application!
  let body = #"{"compressed": ["key": "value"]}"#
  override func setUp() async throws {
    app = try await Application.make(.testing)
  }
  func testAsync() throws {
    let async = true
    XCTAssertTrue(async)
  }
}
