final class Sample {
  Sample() {
    start();
  }

  void start() {}
}

class Other {
  Other() {
    stop();
    helper();
    Runnable task = () -> start();
  }

  private void stop() {}

  static void helper() {}

  void start() {}
}
