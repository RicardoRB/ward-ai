final class NestedBlocksPassing {
  static {
    System.out.println("init");
  }
  void run(boolean a) {
    if (a) {
      run(a);
    }
  }
}
