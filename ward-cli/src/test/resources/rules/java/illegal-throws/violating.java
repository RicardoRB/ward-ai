final class Throws {
  void run() throws RuntimeException {
    risky();
  }

  void risky() throws Throwable {
  }

  void finalize() throws Error {
  }
}
