let a = myList.filter { $0 % 2 == 0 }.count > 0
let b = myList.filter(where: someFunction).count == 0
let c = myList.filter { $0 > 1 }.count != 0
