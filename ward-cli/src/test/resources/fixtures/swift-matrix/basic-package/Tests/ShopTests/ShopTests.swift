@testable import Shop
import XCTest

class ShopTests: XCTestCase {
    func testAddsAnItem() {
        var cart = Cart()
        cart.add("Book")
        XCTAssertEqual(cart.items, ["Book"])
    }

    func testServiceSavesTheAddedItem() {
        let repository = InMemoryCartRepository()
        CartService(cartRepository: repository).addItem("Book")
        XCTAssertEqual(repository.load().items, ["Book"])
    }
}
