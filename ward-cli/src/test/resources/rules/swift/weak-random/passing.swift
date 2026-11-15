import Foundation

func token() -> Int {
    var generator = SystemRandomNumberGenerator()
    let first = Int.random(in: 0..<100, using: &generator)
    let second = Int.random(in: 0..<100)
    return first + second
}
