package app;

import java.util.List;
import static java.lang.Math.max;

public class Shapes {
  private int field = 1;
  static final int LIMIT = 2;

  Shapes(int seed) {
    this.field = seed;
  }

  int measure(List<String> names, final int bound) {
    int total = bound + field;
    int unused = 3;
    for (int i = 0; i < bound; i++) {
      total += i;
    }
    for (String name : names) {
      total += name.length();
    }
    int shadow = 1;
    {
      int shadow2 = shadow;
    }
    return max(total, LIMIT) + missing;
  }

  class Inner {
    int own;

    void touch() {
      field++;
      own = field;
    }
  }
}
