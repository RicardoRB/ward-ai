package fixture

fun use(value: String?) {
    requireNotNull(value)
    println(value)
}
