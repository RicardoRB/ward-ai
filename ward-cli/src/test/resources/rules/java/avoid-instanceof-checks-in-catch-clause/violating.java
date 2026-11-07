class Handler {
  void run() {
    try {
      step();
    } catch (RuntimeException e) {
      if (e instanceof IllegalStateException) {
        recover();
      }
    }
  }

  void step() {}
  void recover() {}
}
