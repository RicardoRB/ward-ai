class Handler {
  void run() {
    try {
      step();
    } catch (IllegalStateException e) {
      recover();
    }
  }

  void step() {}
  void recover() {}
}
