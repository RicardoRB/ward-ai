package fixture

/** Documents the class. */
class Store {
    /** Documents the field. */
    private val items = mutableListOf<String>()

    fun add(item: String) {
        /** Not allowed here. */
        items.add(item)
    }
}

/** Dangling at the end of file. */
