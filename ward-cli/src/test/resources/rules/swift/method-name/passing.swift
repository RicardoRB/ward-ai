func build() {}
struct Sales {
    static func == (lhs: Sales, rhs: Sales) -> Bool { true }
}
struct Options {
    static func `protocol`(_ name: String) -> Options { Options() }
}
