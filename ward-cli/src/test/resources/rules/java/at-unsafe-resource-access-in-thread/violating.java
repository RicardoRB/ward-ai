class Worker {
  private StringBuilder log;

  void start() {
    new Thread(() -> {
      log = new StringBuilder();
      show();
    }).start();
  }

  void show() {
    log.append("x");
  }
}
