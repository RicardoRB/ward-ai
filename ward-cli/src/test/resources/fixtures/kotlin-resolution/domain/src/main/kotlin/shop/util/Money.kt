package shop.util

import shop.domain.Discount
import shop.domain.Price as Cost
import shop.domain.*

fun Cost.toMoney(): String = "${cents} $CURRENCY"

fun Discount.describe(): String = "$percent%"
