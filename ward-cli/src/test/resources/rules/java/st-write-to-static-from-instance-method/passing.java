final class Check {
  private static int count;
  private int size;

  static void reset() {
    count = 0;
  }

  void shadow(int count) {
    count = 3;
  }

  void own() {
    size = 2;
    this.size = 3;
  }
}
