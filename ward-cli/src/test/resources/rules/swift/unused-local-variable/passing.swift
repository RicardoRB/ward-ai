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

func httpVersion() -> String {
    #if os(Linux)
    let version = "HTTP/1.1"
    #else
    let version = String(describing: 1.1)
    #endif
    return version
}

func tracked(session: Session?) {
    var current: Session? = session
    #if compiler(>=6.2)
    weak let weakSession = current
    #else
    weak var weakSession = current
    #endif
    current = nil
    print(weakSession as Any)
}
