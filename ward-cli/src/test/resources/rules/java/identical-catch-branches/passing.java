class Passing {
  void run() {
    try {
      go();
    } catch (IllegalArgumentException e) {
      stop();
    } catch (IllegalStateException e) {
      go();
    }
    try {
      go();
    } catch (java.io.FileNotFoundException e) {
      stop();
    } catch (java.io.IOException e) {
      stop();
    }
  }
  void go() {
  }
  void stop() {
  }
}
