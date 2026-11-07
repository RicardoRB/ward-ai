package fixture

class Always {
    override fun equals(other: Any?): Boolean = true
}

class Never {
    override fun equals(other: Any?): Boolean {
        return false
    }
}
