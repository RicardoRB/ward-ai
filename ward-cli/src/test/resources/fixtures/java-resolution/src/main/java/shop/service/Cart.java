package shop.service;

import shop.domain.Order;

public final class Cart {
  private final Order order = new Order();

  public Order order() {
    return order;
  }
}
