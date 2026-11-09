protocol Drawable {
    func draw()
}
struct Canvas: Drawable {
    func draw() {}
}
let shape: Drawable = Canvas()
