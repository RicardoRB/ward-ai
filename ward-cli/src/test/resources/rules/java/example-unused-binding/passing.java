import java.util.List;

class Work {
  private int field;

  int run(List<Integer> values, int bound) {
    int total = bound;
    for (int value : values) {
      total += value;
    }
    try (var reader = open()) {
      field = total;
    }
    Runnable task = () -> field++;
    task.run();
    return total + field;
  }
}
