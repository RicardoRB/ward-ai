class Sample {
  void run() {
    try {
      run();
    } catch (IllegalStateException e) {
    }
  }
}
