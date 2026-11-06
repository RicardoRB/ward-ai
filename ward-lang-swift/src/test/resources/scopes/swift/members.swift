import Foundation
import struct Mod.Thing

struct Point {
    var x: Int
    let y: Int = 2
    static var origin = Point(x: 0)

    init(x: Int) { self.x = x }

    func sum(with other: Point, _ extra: Int = 1) -> Int {
        let total = x + other.y
        var count = 0
        let unused = 3
        for i in 0..<extra { count += i }
        for (k, v) in dict { print(k) }
        return total + count + missing
    }

    var doubled: Int {
        get { return x * 2 }
        set { x = newValue / 2 }
    }
}

func top(a: Int) -> Int { a }
let global = top(a: 1)
