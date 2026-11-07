package fixture

class Always {
    override fun equals(other: Any?): Boolean = other is Always
}

class Never {
    override fun equals(other: Any?): Boolean {
        return other is Never
    }
}
