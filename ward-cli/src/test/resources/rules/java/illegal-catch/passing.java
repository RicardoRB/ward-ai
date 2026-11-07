final class Catches {
  void run() {
    try {
      risky();
    } catch (java.io.IOException error) {
      throw new IllegalStateException(error);
    }
  }

  void risky() {}
}
