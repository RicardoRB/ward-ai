package shop.util

import shop.domain.Price

fun formatPrice(price: Price): String = price.toMoney()

val CURRENCY: String = "EUR"
