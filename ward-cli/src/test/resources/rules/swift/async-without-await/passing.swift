func load() async -> Int {
    return await fetch()
}
protocol Loader {
    func load() async -> Int
    func save(_ value: Int, to target: String) async throws
}
protocol Cached: Loader {}
struct Fixed: Cached {
    func load() async -> Int { 42 }
}
final class Store {}
extension Store: Loader {
    func load() async -> Int { 1 }
    func save(_ value: Int, to target: String) async throws {}
}
