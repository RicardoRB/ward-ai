let k: Int = 0
let closure: () -> Void = { [k] in
    print(k)
}
func f(p: Int) {
    var local = 0
    let c: () -> Void = { [local, p] in print(local, p) }
    _ = c
}
lazy var lz: Int = 0
let c2: () -> Void = { [lz] in print(lz) }
let c3: () -> Void = { [x = k] in print(x) }
