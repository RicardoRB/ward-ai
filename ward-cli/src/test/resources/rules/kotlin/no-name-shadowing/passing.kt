package fixture

fun siblings(flag: Boolean) {
    if (flag) {
        val value = 1
        println(value)
    }
    if (!flag) {
        val value = 2
        println(value)
    }
    run { val item = 1; println(item) }
    run { val item = 2; println(item) }
}

fun later() {
    run {
        val late = 1
        println(late)
    }
    val late = 2
    println(late)
}

fun underscoreAndIt(xs: List<Int>) {
    xs.forEach { _ -> }
    println(xs.map { it + 1 })
}

fun nested(total: Int) {
    fun helper(total: Int) = total
    println(helper(total))
}
