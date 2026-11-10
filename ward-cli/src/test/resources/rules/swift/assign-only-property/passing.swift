struct Counter {
    private var count = 0
    func value() -> Int {
        return count
    }
    mutating func reset() {
        count = 0
    }
}
