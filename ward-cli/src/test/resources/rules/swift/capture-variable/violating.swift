var j: Int = 0
let closure: () -> Void = { [j] in
    print(j)
}
var c = 0
let closure2: () -> Void = { [c] in
    print(c)
}
class Holder {
    var inst = 0
    func m() {
        let closure3: () -> Void = { [inst] in print(inst) }
        _ = closure3
    }
}
