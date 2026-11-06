final class Loops {
  void run(int[] values) {
    outer:
    for (int value : values) {
      for (int other : values) {
        if (value > other) {
          break outer;
        }
      }
    }
  }
}
