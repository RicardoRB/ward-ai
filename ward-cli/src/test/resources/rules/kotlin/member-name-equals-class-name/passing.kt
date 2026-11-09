package fixture

class Thing {
    val size = 1

    companion object {
        fun Thing(): Thing = Thing()
    }
}
