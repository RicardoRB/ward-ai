import ShopKit
import XCTest

final class CartTests: XCTestCase {
    func testAddsAnItem() {
        var cart = Cart()
        cart.add("Book")
        XCTAssertEqual(cart.items, ["Book"])
    }
}
