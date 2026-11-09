package fixture

class Strings(private val locale: String) {
    companion object {
        fun blank(value: String): Boolean = value.isBlank()
    }
}
