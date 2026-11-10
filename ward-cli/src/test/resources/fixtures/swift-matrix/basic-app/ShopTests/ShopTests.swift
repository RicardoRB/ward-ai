import ShopKit
import Testing

@Suite final class CartTests {
    @Test func addsAnItem() {
        var cart = Cart()
        cart.add("Book")
        #expect(cart.items == ["Book"])
    }

    @Test func serviceSavesTheAddedItem() {
        let repository = InMemoryCartRepository()
        CartService(cartRepository: repository).addItem("Book")
        #expect(repository.load().items == ["Book"])
    }
}
