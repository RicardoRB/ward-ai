import Foundation

func token() -> Int {
    srand(42)
    let first = rand()
    let second = random()
    return Int(first) + Int(second)
}
