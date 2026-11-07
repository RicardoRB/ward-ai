class Violating {
  protected void finalize() {
    cleanup();
  }

  void cleanup() {
  }
}
