class ReturnInFinally {
  int run(boolean a) {
    try {
      if (a) {
        return 1;
      }
      work();
    } finally {
      cleanup();
    }
    return 2;
  }
}
