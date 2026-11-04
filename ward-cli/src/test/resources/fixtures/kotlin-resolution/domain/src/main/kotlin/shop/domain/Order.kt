package shop.domain

class Order(val id: Long, val lines: List<Order.Line>) {
    class Line(val price: Price)

    companion object {
        fun empty(): Order = Order(0, emptyList())
    }
}
