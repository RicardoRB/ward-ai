package fixture

class Empty : Iterator<Int> {
    override fun hasNext(): Boolean = false
    override fun next(): Int = if (hasNext()) 0 else throw NoSuchElementException()
}
