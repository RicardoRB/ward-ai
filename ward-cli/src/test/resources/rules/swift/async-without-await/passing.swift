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
final class Session: NSObject, URLSessionDataDelegate {
    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive response: URLResponse) async -> URLSession.ResponseDisposition {
        .allow
    }
}
struct OpenThing {}
extension OpenThing: AppIntent {
    func perform() async throws -> some IntentResult { .result() }
}
