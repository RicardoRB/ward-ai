/// Adds items to the shopper's cart through a repository.
public struct CartService {
    private let repository: CartRepository

    public init(cartRepository: CartRepository) {
        self.repository = cartRepository
    }

    public func addItem(_ item: String) {
        var cart = repository.load()
        cart.add(item)
        repository.save(cart)
    }
}
