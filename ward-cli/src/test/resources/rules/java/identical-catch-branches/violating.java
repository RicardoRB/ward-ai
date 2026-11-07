class Violating {
  void run() {
    try {
      go();
    } catch (IllegalArgumentException e) {
      stop();
    } catch (IllegalStateException e) {
      stop();
    }
  }
  void go() {
  }
  void stop() {
  }
}
