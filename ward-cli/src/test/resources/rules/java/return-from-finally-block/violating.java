class Cleanup {
  int close() {
    try {
      return 1;
    } finally {
      return 2;
    }
  }
}
