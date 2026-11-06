class Sizes(val base: Int) {
    fun total(bound: Int): Int {
        return base + bound + missing
    }
}

fun other(): Int = absent + 1
