final class Loops {
  void run(int[] values) {
    outer:
    for (int value : values) {
      if (value > 0) {
        return;
      }
    }
  }
}
