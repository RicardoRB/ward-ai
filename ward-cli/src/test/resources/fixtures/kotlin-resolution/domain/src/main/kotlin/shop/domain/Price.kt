package shop.domain

class Price(val cents: Long) {
    companion object {
        val ZERO = Price(0)
    }
}
