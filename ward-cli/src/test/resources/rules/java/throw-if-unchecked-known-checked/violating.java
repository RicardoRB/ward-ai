final class T {
  void run(IOException e) {
    Throwables.throwIfUnchecked(e);
  }
}
