package shop.domain;

public final class Price {
  private final int cents;

  public Price(final int cents) {
    this.cents = cents;
  }

  public int cents() {
    return cents;
  }
}
