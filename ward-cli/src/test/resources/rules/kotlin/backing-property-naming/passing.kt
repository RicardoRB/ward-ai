package fixture

class Store {
    private val _items = mutableListOf<String>()
    val items: List<String> get() = _items
}
