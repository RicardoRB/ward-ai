final class Attempts {
  void run() {
    try {
      try {
        try {
          risky();
        } catch (java.io.IOException error) {
          risky();
        }
      } catch (java.io.IOException error) {
        risky();
      }
    } catch (java.io.IOException error) {
      risky();
    }
  }

  void risky() {}
}
