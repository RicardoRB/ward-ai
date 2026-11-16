typealias Progress = @Sendable (_ fraction: Double) -> Void
typealias Loader = (_ url: URL, _ completion: @escaping @Sendable (Result<Data, any Error>) -> Void) -> Void
typealias Fetch = (URL) async throws -> Data

struct Handlers {
    var streams: [@Sendable (_ data: Data) -> Void] = []
    var pending: [(queue: DispatchQueue, handler: (_ event: Event) -> Void)] = []
    let validate: @Sendable (_ request: URLRequest?, _ response: HTTPURLResponse) -> Bool
}

enum Encoding {
    case custom((_ key: String, _ index: Int) -> String)
    case chunked(count: Int, delay: Int = 40)
}

func move(to destination: @escaping (_ temporary: URL) -> (target: URL, options: Options)) {}
