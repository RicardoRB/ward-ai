import XCTest
@testable import Shop

class ShopTests: XCTestCase {
    func testAddsAnItem() {
        var cart = Cart()
        cart.add("Book")
        XCTAssertEqual(cart.items, ["Book"])
    }
}
