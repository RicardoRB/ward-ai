final class T {
  static void foo(Object... a) {}

  void run() {
    foo(null);
  }
}
