struct Point {
    func move(x: Int, y: Int) -> Int {
        return x + y
    }
    func ignored(_value: Int) -> Int {
        return 1
    }
}

class Shape: Drawable {
    func draw(size: Int) {}
}
