package fixture

fun check(count: Int) {
    require(count >= 0) { "negative" }
}
