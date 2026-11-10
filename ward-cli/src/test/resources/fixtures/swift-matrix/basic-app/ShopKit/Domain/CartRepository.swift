/// Loads and saves the shopper's cart.
public protocol CartRepository {
    func load() -> Cart

    func save(_ cart: Cart)
}
