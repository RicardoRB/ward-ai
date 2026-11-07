package fixture

class Empty : Iterator<Int> {
    override fun hasNext(): Boolean = false
    override fun next(): Int = 0
}
