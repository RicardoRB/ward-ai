public final class InMemoryCartRepository: CartRepository {
    private var cart = Cart()

    public init() {}

    public func load() -> Cart {
        cart
    }

    public func save(_ cart: Cart) {
        self.cart = cart
    }
}
