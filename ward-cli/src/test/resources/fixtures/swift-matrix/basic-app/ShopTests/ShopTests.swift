import ShopKit
import XCTest

final class CartTests: XCTestCase {
    func testAddsAnItem() {
        var cart = Cart()
        cart.add("Book")
        XCTAssertEqual(cart.items, ["Book"])
    }

    func testServiceSavesTheAddedItem() {
        let repository = InMemoryCartRepository()
        CartService(repository: repository).addItem("Book")
        XCTAssertEqual(repository.load().items, ["Book"])
    }
}
