package fixture

import java.util.ArrayList

/** Documents the class. */
class Store {
    /** Documents the field. */
    private val items = mutableListOf<String>()

    /** Adds an item. */
    fun add(item: String) {
        items.add(item)
    }
}

/** Documents the enum. */
enum class Level {
    /** Documents the first entry. */
    Low {
        override fun next(): Level = High
    },

    /** Documents the second entry. */
    High {
        override fun next(): Level = Low
    };

    /** Documents the function. */
    abstract fun next(): Level
}

/** Documents the clock. */
class Clock(private val start: Long) {
    constructor() : this(0L)

    /** Documents the function after a constructor without a body. */
    @JvmOverloads
    fun elapsed(now: Long = 0L): Long = now - start
}
