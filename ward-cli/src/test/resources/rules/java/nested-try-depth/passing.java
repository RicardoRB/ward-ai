final class Attempts {
  void run() {
    try {
      risky();
    } catch (java.io.IOException error) {
      risky();
    }
  }

  void risky() {}
}
