class Flows {
  void sink(@Null Object value) {}

  void run(@Nullable String maybe) {
    sink(maybe);
  }
}
