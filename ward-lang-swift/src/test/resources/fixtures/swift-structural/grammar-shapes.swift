import Foundation

protocol Greeter {
    func greet(name: String) -> String
}

struct Store: Greeter {
    let `default`: Int = 1

    init(count: Int = 0) {
        self.count = count
    }

    func greet(name: String) -> String {
        if count > 0 && name != "" || count < 0 {
            return "hi"
        } else if count == 0 {
            return "zero"
        }
        guard count != 1 else { return "one" }
        for item in [1, 2] { print(item) }
        while count < 2 { count += 1 }
        repeat { count -= 1 } while count > 0
        switch count {
        case 1: print("a")
        default: print("c")
        }
        let label = count > 1 ? "y" : "n"
        do { try work() } catch { print(error) }
        let run = { (x: Int) -> Int in return x }
        return label + run(1)
    }
}

class Shape {}

extension Store {
    func extra() {}
}

enum Color {
    case red
}

enum Raw: String {
    case one = "1"
}
