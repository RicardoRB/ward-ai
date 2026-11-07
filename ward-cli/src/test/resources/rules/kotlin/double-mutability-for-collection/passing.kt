package fixture

class Registry {
    val items: List<String> = listOf()
    val byName = mapOf<String, Int>()
    private val hidden: MutableList<String> = mutableListOf()
    fun local(): Int {
        val inner: MutableList<Int> = mutableListOf()
        return inner.size
    }
}
