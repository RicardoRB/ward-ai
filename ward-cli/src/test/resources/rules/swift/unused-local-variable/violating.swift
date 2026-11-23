func compute() -> Int {
    let unused = 1
    return 2
}

func onlyInOneBranch() {
    #if DEBUG
    let flag = 1
    #endif
}
