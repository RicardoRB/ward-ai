class TryCatchFinally {
  void run() {
    try {
      risky();
    } catch (IllegalStateException e) {
      recover();
    } finally {
      cleanup();
    }
    done();
  }
}
