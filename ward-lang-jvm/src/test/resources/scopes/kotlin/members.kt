package app

import a.b.C
import a.b.D as E

class Counter(val start: Int, step: Int) {
    var count = start
    val label: String get() = "n" + field

    constructor(q: Int) : this(q, 1)

    fun bump(by: Int = 1): Int {
        val local = by + count
        var mutable = 0
        for (i in 0..by) {
            mutable += i
        }
        for ((key, value) in table) {
            use(key)
        }
        val unusedLocal = 4
        return local + missing
    }

    companion object {
        const val LIMIT = 10
    }
}

object Registry {
    val items = listOf(1)
}

fun top(a: Int) = a + 1

val topLevel = top(2)
