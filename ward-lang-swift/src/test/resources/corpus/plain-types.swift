enum Direction: String, CaseIterable {
    case north = "N"
    case south = "S"
}

class Counter {
    private(set) var count = 0
    func increment(by amount: Int = 1) { count += amount }
}

extension Counter: CustomStringConvertible {
    var description: String { "Counter(\(count))" }
}
