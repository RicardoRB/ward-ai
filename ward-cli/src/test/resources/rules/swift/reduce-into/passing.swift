let bar = values.reduce(into: "") { $0 += "\($1)" }
let sum = values.reduce(0, +)
