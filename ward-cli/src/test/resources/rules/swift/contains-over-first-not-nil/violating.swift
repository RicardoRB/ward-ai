let a = myList.first { $0 % 2 == 0 } != nil
let b = myList.firstIndex(where: someFunction) == nil
let c = (myList.first { $0 > 1 }) == nil
