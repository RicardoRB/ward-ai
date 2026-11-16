struct Point {
    init(x: Int) {}
    init() {}
    func scale() {}
}

struct Headers {
    private var items: [String] = []

    /// Creates an empty instance.
    init() {}

    /// Creates an instance from the items.
    init(_ items: [String]) {
        self.items = items
    }

    // a plain comment between two initializers
    init(one item: String) {
        items = [item]
    }
}
