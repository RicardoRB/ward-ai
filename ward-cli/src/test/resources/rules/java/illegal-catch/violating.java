final class Catches {
  void run() {
    try {
      risky();
    } catch (RuntimeException error) {
      throw error;
    }
  }

  void risky() {}
}
