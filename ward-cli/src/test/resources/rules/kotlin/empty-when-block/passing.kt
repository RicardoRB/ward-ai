package fixture

fun label(flag: Int): String {
    return when (flag) {
        1 -> "one"
        else -> "other"
    }
}

fun commentedWhen(flag: Int) {
    when (flag) {
        // nothing yet
    }
}
