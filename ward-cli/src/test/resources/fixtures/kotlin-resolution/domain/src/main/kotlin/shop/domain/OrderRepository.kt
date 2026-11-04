package shop.domain

interface OrderRepository {
    fun find(id: Long): Order?
}
