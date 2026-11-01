import Testing
@testable import Shop

@Test func addsAnItem() {
    var cart = Cart()
    cart.add("Book")
    #expect(cart.items == ["Book"])
}

@Test func serviceSavesTheAddedItem() {
    let repository = InMemoryCartRepository()
    CartService(repository: repository).addItem("Book")
    #expect(repository.load().items == ["Book"])
}
