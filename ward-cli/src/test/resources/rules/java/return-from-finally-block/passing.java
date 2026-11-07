class Cleanup {
  int close() {
    try {
      return 1;
    } finally {
      cleanup();
    }
  }

  void cleanup() {}
}
