package fixture

fun use(value: String?) {
    require(value != null)
    println(value)
}
