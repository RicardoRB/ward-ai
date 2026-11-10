package shop.legacy;

import shop.domain.Discount;

public final class Coupon {
  public Discount discount() {
    return new Discount(5);
  }
}
