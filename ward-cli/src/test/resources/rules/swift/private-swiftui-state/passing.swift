struct Counter {
    @State private var count = 0
}

struct Model {
    @StateObject private var store = Store()
    @State fileprivate var open = false
}
