package fixture

class Item {
    override fun toString(): String {
        throw IllegalStateException("no")
    }

    override fun hashCode(): Int = throw IllegalStateException("no")
}
