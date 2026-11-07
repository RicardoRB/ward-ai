class Point {
    var x = 0
    init(x: Int) {
        self.x = x
    }
    func move(by delta: Int) {
        x += delta
    }
}
