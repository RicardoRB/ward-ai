func load() async -> Int {
    return 1
}
protocol Loader {
    func load() async -> Int
}
struct Other: Loader {
    func load() async -> Int { 1 }
    func load(again: Bool) async -> Int { 2 }
    func nested() async -> Int {
        func inner() async -> Int { 3 }
        return 4
    }
}
struct Plain {
    func load() async -> Int { 5 }
}
