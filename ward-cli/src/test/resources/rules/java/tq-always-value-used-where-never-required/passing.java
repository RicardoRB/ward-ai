class Flows {
  void sink(@Null Object value) {}

  void run(@Nullable String maybe, String unknown) {
    sink(null);
    sink(unknown);
  }
}
