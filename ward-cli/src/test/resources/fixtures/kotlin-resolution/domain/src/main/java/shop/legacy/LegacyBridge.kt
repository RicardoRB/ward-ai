package shop.legacy

import shop.domain.Coupon

class LegacyBridge(val coupon: Coupon) {
    fun apply(price: shop.domain.Price): Long = coupon.apply(price)
}
