package fixture

class Item {
    override fun toString(): String = "item"

    fun load() {
        throw IllegalStateException("no")
    }
}
