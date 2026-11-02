public struct CartService {
    private let repository: CartRepository

    public init(repository: CartRepository) {
        self.repository = repository
    }

    public func addItem(_ item: String) {
        let unused = 1
        var cart = repository.load()
        cart.add(item)
        repository.save(cart)
    }
}
