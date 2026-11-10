func largest<T: Comparable>(_ items: [T]) -> T? {
    items.max()
}

func equal<S1, S2>(_ first: S1, _ second: S2) -> Bool
    where S1: Sequence, S2: Sequence, S1.Element == S2.Element, S1.Element: Equatable {
    return Array(first) == Array(second)
}

struct Stack<Element> {
    private var items: [Element] = []
    mutating func push(_ item: Element) { items.append(item) }
}
