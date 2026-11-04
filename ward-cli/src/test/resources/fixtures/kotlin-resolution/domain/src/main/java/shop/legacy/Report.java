package shop.legacy;

import shop.domain.Coupon;
import shop.domain.Price;
import shop.util.FormatKt;
import shop.util.Texts;

public final class Report {
  public String line(final Price price, final Coupon coupon) {
    return Texts.shout(FormatKt.formatPrice(price)) + coupon.apply(price);
  }
}
