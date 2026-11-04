package shop.domain;

import java.util.List;

public final class Repository {
  public Order find(final String id) {
    return new Order(id.length());
  }

  public List<Order> all() {
    return List.of(new Order(2));
  }
}
