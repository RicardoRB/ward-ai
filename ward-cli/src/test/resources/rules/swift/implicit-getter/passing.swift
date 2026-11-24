struct Box {
    var size: Int { 1 }
}

struct Holder {
    var value: Int {
        get { 1 }
        nonmutating set { }
    }
    var other: Int {
        mutating get { 1 }
        set { }
    }
    var inline: Int {
        get { 1 } mutating set { }
    }
}
