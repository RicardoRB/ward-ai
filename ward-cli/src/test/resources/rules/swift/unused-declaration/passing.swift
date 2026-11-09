func used() -> Int {
    return 2
}
let value = used()
public func exported() {}
@objc func selectorTarget() {}
protocol Drawable {
    func draw()
}
struct Canvas: Drawable {
    func draw() {}
}
