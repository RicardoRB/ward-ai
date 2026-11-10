class Sample {
  static void log(Object... parts) {}

  void run(String[] words) {
    log(words);
    log(null);
  }
}
