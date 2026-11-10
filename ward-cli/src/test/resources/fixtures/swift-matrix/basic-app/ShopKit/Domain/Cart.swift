/// The items the shopper has added to the cart.
public struct Cart {
    public private(set) var items: [String] = []

    public init() {}

    public mutating func add(_ item: String) {
        items.append(item)
    }
}
