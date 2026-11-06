func work(path: String, items: [Int]) -> Int {
    var total = 0
    let (first, second) = (items[0], items[1])
    for item in items {
        total += item
    }
    if let found = items.first {
        total += found
    }
    return total + first
}
