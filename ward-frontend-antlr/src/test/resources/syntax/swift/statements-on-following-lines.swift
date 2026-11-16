final class Cache {
    let request: Request
    weak var owner: Owner?
    var name: String { request.name }

    init(request: Request) {
        self.request = request
    }

    func load() -> Int {
        let total = request.size
            + request.overhead
        let value = total
        (owner as? Loader)?.load()
        [value].forEach { print($0) }
        return value
    }

    func check() {
        if items.contains(where: { $0.isEmpty }) {
            print("empty")
        }
        while let next = iterator.next() {
            print(next)
        }
        for item in items where item.isValid {
            print(item)
        }
    }
}
