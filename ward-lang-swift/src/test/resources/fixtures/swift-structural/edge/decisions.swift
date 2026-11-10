func decisions(value: Int, items: [Int]) throws -> Int {
    if value > 0 {
        return 1
    } else if value < 0 {
        return -1
    }
    guard value != 7 else { return 7 }
    for item in items { print(item) }
    while value > 100 { print(value) }
    repeat { print(value) } while value > 200
    switch value {
    case 1: print("one")
    case 2: print("two")
    default: print("other")
    }
    let label = value > 3 ? "big" : "small"
    if value > 4 && value < 9 || value == 42 { print(label) }
    do { try check() } catch { print(error) }
    return 0
}
