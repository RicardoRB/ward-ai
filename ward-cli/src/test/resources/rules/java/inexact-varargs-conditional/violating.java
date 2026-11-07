final class Violating {
  void run(boolean flag, Object[] array) {
    print(flag ? array : "x");
  }
  void print(Object... values) {
  }
}
