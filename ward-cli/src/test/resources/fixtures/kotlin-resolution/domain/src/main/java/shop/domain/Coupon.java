package shop.domain;

public final class Coupon {
  private final Discount discount;

  public Coupon(final Discount discount) {
    this.discount = discount;
  }

  public long apply(final Price base) {
    return base.getCents() - discount.getPercent();
  }
}
