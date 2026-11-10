final class T {
  void run(Optional<String> o) {
    o.map(s -> s.length());
  }
}
