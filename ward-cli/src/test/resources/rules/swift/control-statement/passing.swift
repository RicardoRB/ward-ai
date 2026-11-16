func check(flag: Bool) {
    if flag {
        print(flag)
    }
}

func compare(left: Int, right: Int) {
    switch (left, right) {
    case (1, _): print(left)
    default: print(right)
    }
}
