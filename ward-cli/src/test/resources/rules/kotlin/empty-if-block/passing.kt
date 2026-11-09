package fixture

fun check(flag: Boolean) {
    if (flag) {
        println(1)
    }
    if (flag) {
        // intentionally empty
    }
}
