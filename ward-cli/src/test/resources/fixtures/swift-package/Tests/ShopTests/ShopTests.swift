@testable import Shop
import XCTest

class ShopTests: XCTestCase {
    func testAddsAnItem() {
        var cart = Cart()
        cart.add("Book")
        XCTAssertEqual(cart.items, ["Book"])
    }
}
