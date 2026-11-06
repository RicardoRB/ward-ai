let allNines = nums.allSatisfy { $0 == 9 }
let total = nums.reduce(0, +)
let ok = nums.reduce(into: true) { $0 = $0 && $1 == 9 }
