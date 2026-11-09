package fixture

fun use(value: String?) {
    checkNotNull(value)
    println(value)
}
