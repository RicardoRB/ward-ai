func setUp(queue: DispatchQueue) {
    let configuration = SessionConfiguration.default
    let files = FileManager.default.temporaryDirectory
    queue.async {
        print(configuration, files)
    }
    let name = value.as(String.self)
    let first = pair.0
    let total = counts.reduce(0) { $0 + $1.1 }
    print(name ?? "", first, total)
}
