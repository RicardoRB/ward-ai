package shop.service;

import shop.domain.Order;

public final class CheckoutTest {
  public Checkout checkout() {
    return new Checkout();
  }

  public Order order() {
    return new Order();
  }
}
