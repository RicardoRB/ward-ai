final class Catches {
  void run() {
    try {
      risky();
    } catch (IllegalStateException error) {
      throw error;
    }
  }

  void risky() {}
}
