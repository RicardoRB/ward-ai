import java.util.List;
import static java.lang.Math.max;

class Sizes {
  private int base = 1;
  static final int LIMIT = 2;

  int total(List<String> names, int bound) {
    int sum = base + bound;
    for (String name : names) {
      sum += name.length();
    }
    Runnable run = () -> System.out.println(sum + LIMIT);
    return max(sum, LIMIT) + Other.VALUE;
  }
}

class Child extends Parent {
  int read() {
    return inherited;
  }
}
