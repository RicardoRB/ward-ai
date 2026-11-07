final class T {
  void run(Optional<String> o) {
    // ok
    o.map(s -> s.length()).orElse(0);
    int n = o.map(s -> s.length()).get();
  }
}
