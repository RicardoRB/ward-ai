func run(optional: Int?, other: Int?) -> Int {
    guard let unwrapped = optional else { return 0 }
    if let a = optional, var b = other {
        print(a, b)
    } else {
        print(a)
    }
    let closure = { (n: Int) -> Int in n + 1 }
    let short = { $0 + 1 }
    items.map { item in item + unknown }
    switch unwrapped {
    case let v where v > 1:
        print(v)
    case .some(let w):
        print(w)
    default:
        break
    }
    do {
        try risky()
    } catch let failure {
        print(failure)
    }
    do { try risky() } catch { print(error) }
    return unwrapped + closure(1) + short(2)
}

enum Direction: Int {
    case north = 1, south
    func flip() -> Direction { return north }
}

class Base {}
class Derived: Base {
    func use() { inherited() }
}

extension Point {
    func more() { y }
}
