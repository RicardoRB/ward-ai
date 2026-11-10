class NestedIf {
  void run(boolean a, boolean b) {
    if (a) {
      if (b) {
        x();
      }
    }
    y();
  }
}
