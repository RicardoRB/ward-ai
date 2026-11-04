package shop.api

import shop.domain.OrderRepository

fun lineCount(repo: OrderRepository): Int {
    val order = repo.find(1L)
    return order?.lines?.size ?: 0
}
