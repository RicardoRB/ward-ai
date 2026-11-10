package shop.domain;

public final class Order {
  private final int total;

  public Order(final int total) {
    this.total = total;
  }

  public int total() {
    return total;
  }
}
