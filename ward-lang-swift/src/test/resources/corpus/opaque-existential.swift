protocol Shape {
    func area() -> Double
}

func makeShape() -> some Shape {
    Square(side: 2)
}

func describe(_ shapes: [any Shape]) -> Int {
    shapes.count
}

struct Square: Shape {
    let side: Double
    func area() -> Double { side * side }
}
