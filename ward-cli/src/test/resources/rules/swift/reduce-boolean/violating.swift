let allNines = nums.reduce(true) { $0 && $1 == 9 }
let anyNines = nums.reduce(false, { $0 || $1 == 9 })
