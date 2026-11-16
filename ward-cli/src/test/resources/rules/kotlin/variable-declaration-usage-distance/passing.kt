fun total(values: List<Int>): Int {
    var sum = 0
    sum += values.first()
    return sum
}

open class Base

class Cursor : Base() {
    var offset: Long = 0L
    var start = -1
    var end = -1

    fun first(): Int = 1

    fun second(): Int = 2

    fun third(): Int = 3

    fun reset() {
        offset = 0L
    }
}
