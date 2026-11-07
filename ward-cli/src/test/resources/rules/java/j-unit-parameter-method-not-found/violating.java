class Violating {
  @Parameters(method = "present,missing")
  void run() {
  }

  Object present() {
    return null;
  }
}
