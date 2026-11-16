func merge(_ sales: inout [Int: Int], with other: [Int: Int], id: Int) {
    sales[id, default: 0] += other[id, default: 0]
    if #available(iOS 17, macOS 14, tvOS 17, watchOS 10, visionOS 1, *) {
        print(sales[id] ?? 0)
    }
}

func paths(_ monitors: Protected<[Monitor]>) {
    let all = monitors.read(\.self)
    let names = all.map(\.name)
    let first = \Monitor.children[0].name
    print(names, first)
}
