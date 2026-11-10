package shop.service;

import shop.domain.Discount;

public final class Pricing {
  public Discount none() {
    return new Discount(0);
  }

  public shop.domain.Price base() {
    return new shop.domain.Price(10);
  }
}
