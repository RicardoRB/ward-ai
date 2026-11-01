public protocol CartRepository {
    func load() -> Cart

    func save(_ cart: Cart)
}
