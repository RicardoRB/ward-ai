package shop.domain;

import java.util.ArrayList;
import java.util.List;

public final class Order {
  private final List<Line> lines = new ArrayList<>();

  public int total() {
    int sum = 0;
    for (final Line line : lines) {
      sum += line.price().cents();
    }
    return sum;
  }

  public static final class Line {
    private final Price price;

    public Line(final Price price) {
      this.price = price;
    }

    public Price price() {
      return price;
    }
  }
}
