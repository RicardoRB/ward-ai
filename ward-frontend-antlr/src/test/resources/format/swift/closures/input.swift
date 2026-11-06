let doubled = numbers.map { $0 * 2 }
numbers.forEach { number in
print(number)
}
let sorted = names.sorted { lhs, rhs in
lhs < rhs
}
