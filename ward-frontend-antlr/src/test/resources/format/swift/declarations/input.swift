import Foundation

struct Point {
var x: Int
var y: Int
func distance(to other: Point) -> Double {
return ((x - other.x) * (x - other.x) + (y - other.y) * (y - other.y)).squareRoot()
}
}

protocol Shape {
func area() -> Double
}

enum Direction {
case north
case south
}

extension Point: Shape {
func area() -> Double { 0 }
}

class Canvas {
let width: Int
init(width: Int) {
self.width = width
}
}
