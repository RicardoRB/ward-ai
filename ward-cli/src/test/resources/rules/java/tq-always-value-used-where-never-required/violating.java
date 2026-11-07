class Flows {
  void sink(@Null Object value) {}

  void run(@NonNull String always) {
    sink(always);
    sink("text");
  }
}
