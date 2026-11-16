class Point {
    var x = 0
    init(x: Int) {
        self.x = x
    }
    func move(by delta: Int) {
        x += delta
    }
}

public struct Box {
    public var rawValue: Int

    public init(rawValue: Int) {
        self.rawValue = rawValue
    }

    public convenience init?(rawValue: Int, label: String) {
        self.init(rawValue: rawValue)
    }
}
