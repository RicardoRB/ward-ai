func compute() -> Int {
    let used = 1
    return used
}

func describe() -> String {
    let duration = 3
    let unit = "s"
    return "took \(duration)\(unit)"
}

func report() {
    let result = load()
    print("""
        received: \(String(describing: result))
        """)
}

func totals(of sales: [Int]) -> Int {
    var previous: Int?
    var sum = 0
    for sale in sales {
        if let previous = previous {
            sum += previous
        }
        previous = sale
    }
    return sum
}
