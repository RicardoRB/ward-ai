class Logic {
  void run(boolean flag) {
    if (flag) {
      log();
    } else {
      skip();
    }
  }

  void log() {}
  void skip() {}
}
