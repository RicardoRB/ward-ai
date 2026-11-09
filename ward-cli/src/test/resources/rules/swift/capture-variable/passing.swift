let k: Int = 0
let closure: () -> Void = { [k] in
    print(k)
}
func f() {
    var local = 0
    let closure: () -> Void = { [local] in print(local) }
}
