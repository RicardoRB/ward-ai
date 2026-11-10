package shop.service;

import java.util.List;
import shop.domain.*;
import static shop.domain.Prices.ZERO;

public final class Checkout {
  private final Cart cart = new Cart();
  private final List<Order.Line> lines = new java.util.ArrayList<>();

  public Price total() {
    final Order order = cart.order();
    return order.total() == 0 ? ZERO : new Price(order.total());
  }
}
