var j: Int = 0
let closure: () -> Void = { [j] in
    print(j)
}
var c = 0
let closure2: () -> Void = { [c] in
    print(c)
}
