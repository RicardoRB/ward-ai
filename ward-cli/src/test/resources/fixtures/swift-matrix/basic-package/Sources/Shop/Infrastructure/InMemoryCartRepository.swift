/// Keeps the cart in memory, for tests and previews.
public final class InMemoryCartRepository: CartRepository {
    private var cart = Cart()

    public init() {}

    public func load() -> Cart {
        cart
    }

    public func save(_ updated: Cart) {
        cart = updated
    }
}
