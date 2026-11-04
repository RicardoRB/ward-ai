package shop.api

import java.time.Instant
import shop.domain.Coupon
import shop.domain.Order
import shop.domain.Price
import shop.util.formatPrice

class Checkout(private val coupon: Coupon) {
    fun run(line: Order.Line, at: Instant): String {
        val base = Price.ZERO
        return formatPrice(base) + generatedGreeting() + coupon.apply(base)
    }
}
