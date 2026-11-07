package fixture

class Bad(private val items: List<Int>) : Iterator<Int> {
    private var index = 0
    override fun hasNext(): Boolean {
        return index < items.size
    }
    override fun next(): Int = items[index++]
}
