package sample

import kotlin.math.max

class Counter(private var value: Int = 0) {
    fun increment(step: Int = 1): Int {
        value += step
        return value
    }
}

fun total(values: List<Int>): Int =
        values.fold(0) { acc, value ->
            acc + value
        }
