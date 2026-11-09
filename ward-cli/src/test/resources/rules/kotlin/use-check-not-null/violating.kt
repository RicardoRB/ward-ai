package fixture

fun use(value: String?) {
    check(value != null)
    println(value)
}
