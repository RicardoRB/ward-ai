class Cleanup {
  void close() {
    try {
      run();
    } catch (IllegalStateException e) {
      throw e;
    }
  }

  void run() {}
}
