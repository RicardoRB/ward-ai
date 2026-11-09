perform { elem in
    elem.isEmpty() ? .emptyValue() : .number(elem)
    return 1
}
subscript(index: Int) -> Int {
    index == 0 ? something() : somethingElse(index)
    return index
}
func run() {
    flag ? doA() : doB()
    done()
}
