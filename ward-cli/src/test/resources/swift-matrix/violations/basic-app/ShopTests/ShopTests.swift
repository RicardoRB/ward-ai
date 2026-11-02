import ShopKit
import XCTest

final class CartTests: XCTestCase {
    func testFailsOnPurpose() {
        XCTAssertEqual(Cart().items, ["Book"])
    }
}
