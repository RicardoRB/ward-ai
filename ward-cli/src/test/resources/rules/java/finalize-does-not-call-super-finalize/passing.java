class Pool {
  void finalize() throws Throwable {
    cleanup();
    super.finalize();
  }

  void cleanup() {}
}
