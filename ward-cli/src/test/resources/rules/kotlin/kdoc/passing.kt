package fixture

/** Documents the class. */
class Store {
    /** Documents the field. */
    private val items = mutableListOf<String>()

    /** Adds an item. */
    fun add(item: String) {
        items.add(item)
    }
}
