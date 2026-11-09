package fixture

fun version(): String = "1.0" + suffix()

fun suffix(): String {
    return compute()
}

fun compute() = "x" + "y"

class Cfg {
    fun version() = "1"
}
