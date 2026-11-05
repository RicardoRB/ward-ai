class ThrowOutsideTry {
  void run(boolean a) {
    if (a) {
      throw new IllegalStateException();
    }
    done();
  }
}
