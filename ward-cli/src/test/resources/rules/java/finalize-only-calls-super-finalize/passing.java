class Pool {
  void finalize() {
    cleanup();
    super.finalize();
  }

  void cleanup() {}
}
