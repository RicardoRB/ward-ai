import Foundation

struct Sizes {
    var base = 1

    func total(items: [Int], bound: Int) -> Int {
        var sum = bound
        for item in items {
            sum += item
        }
        guard let first = items.first else { return sum }
        let doubled = items.map { $0 * 2 }
        print(doubled, first, Date())
        return sum + base
    }
}

class Child: Parent {
    func read() { inherited() }
}
