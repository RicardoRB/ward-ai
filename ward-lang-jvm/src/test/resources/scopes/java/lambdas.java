import java.util.function.Function;

class Lambdas extends Base {
  void run(Object o, int k) {
    Function<Integer, Integer> inc = x -> x + k;
    Runnable r = () -> {
      int inner = k;
    };
    try (var resource = open()) {
      use(resource);
    } catch (IllegalStateException | IllegalArgumentException e) {
      throw e;
    }
    if (o instanceof String str && str.isEmpty()) {
      use(str);
    }
    switch (k) {
      case 1:
        int scoped = 1;
        break;
      default:
        break;
    }
    Object anon = new Object() {
      int hidden = 1;
      int show() { return hidden + k; }
    };
    inherited = 2;
  }
}
