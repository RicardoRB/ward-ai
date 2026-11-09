package fixture

fun pick(flag: Boolean): Int {
    if (flag) {
        return 1
    } else {
        return 2
    }
}

fun commentedElse(flag: Boolean): Int {
    if (flag) {
        return 1
    } else {
        // nothing yet
    }
    return 0
}
