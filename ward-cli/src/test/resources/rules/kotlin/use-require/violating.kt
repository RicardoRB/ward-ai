package fixture

fun check(count: Int) {
    if (count < 0) throw IllegalArgumentException("negative")
}
