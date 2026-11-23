struct Point {
    func move(x: Int, y: Int) -> Int {
        return x
    }
}

struct Other {
    func skip(transaction: Int, other: Int) -> Int {
        guard case .some(let value) = Optional(other) else {
            return 0
        }
        return value
    }
}
