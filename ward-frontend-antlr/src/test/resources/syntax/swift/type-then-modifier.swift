final class Store {
    private let files: FileManager

    weak var provider: (any StateProvider)?

    let request: URLRequest
    override var current: URLRequest? { request }
}
