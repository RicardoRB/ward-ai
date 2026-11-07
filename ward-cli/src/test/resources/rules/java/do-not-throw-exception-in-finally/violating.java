class Cleanup {
  void close() {
    try {
      run();
    } finally {
      throw new IllegalStateException();
    }
  }

  void run() {}
}
