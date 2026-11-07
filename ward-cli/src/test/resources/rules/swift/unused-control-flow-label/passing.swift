func f(xs: [Int]) {
    outer: for x in xs {
        break outer
    }
}
