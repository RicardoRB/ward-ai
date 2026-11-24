import Foundation

func token() -> Int {
    var generator = SystemRandomNumberGenerator()
    let first = Int.random(in: 0..<100, using: &generator)
    let second = Int.random(in: 0..<100)
    return first + second
}

struct SeededRandomGenerator: RandomNumberGenerator {
    init(seed: Int) {
        srand48(seed)
    }

    func next() -> UInt64 {
        UInt64(drand48() * Double(UInt64.max))
    }
}

extension FixedWidthInteger {
    public static func random() -> Self {
        return Self.random(in: .min ... .max)
    }
}

func rand() -> Int {
    return 4
}

func useOwn() -> Int {
    return rand()
}
