extension [Header] {
    var names: [String] { map(\.name) }
}

extension [String: Int] {
    var total: Int { values.reduce(0, +) }
}

extension Box: Equatable where Value: Equatable {
    static func == (lhs: Box, rhs: Box) -> Bool { lhs.value == rhs.value }
}
