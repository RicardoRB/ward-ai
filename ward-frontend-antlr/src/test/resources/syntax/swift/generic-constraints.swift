public protocol Serializer<Output>: Sendable {
    associatedtype Output: Decodable & Sendable
}

public protocol Uploadable: Convertible & Sendable {}

typealias Parameters = [String: any Any & Sendable]

extension ButtonStyle where Self == RoundedStyle {
    static var rounded: RoundedStyle { RoundedStyle() }
}

extension Collection where Self.Element: Equatable {
    func has(_ element: Element) -> Bool { contains(element) }
}

open class Session: @unchecked Sendable {
    public subscript<T>(dynamicMember keyPath: KeyPath<Product, T>) -> T {
        product[keyPath: keyPath]
    }
}
