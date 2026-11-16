package fixture

class Item {
    override fun toString(): String = "item"

    fun load() {
        throw IllegalStateException("no")
    }
}

class Reader {
    fun stream(): Any {
        return object : Any() {
            fun read(): Int {
                throw IllegalStateException("closed")
            }

            override fun toString(): String = "stream"

            fun skip() {
                throw IllegalStateException("closed")
            }
        }
    }
}
