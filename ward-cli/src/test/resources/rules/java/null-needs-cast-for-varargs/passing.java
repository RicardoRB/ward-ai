final class T {
  static void foo(Object... a) {}

  void run() {
    // ok
    foo((Object[]) null);
    foo(null, null);
  }
}
