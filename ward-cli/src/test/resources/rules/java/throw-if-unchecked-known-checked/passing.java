final class T {
  void run(Exception e) {
    // ok
    Throwables.throwIfUnchecked(e);
  }
}
