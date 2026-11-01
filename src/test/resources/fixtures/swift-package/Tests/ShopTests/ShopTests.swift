import Testing
@testable import Shop

@Test func addsAnItem() {
    var cart = Cart()
    cart.add("Book")
    #expect(cart.items == ["Book"])
}
