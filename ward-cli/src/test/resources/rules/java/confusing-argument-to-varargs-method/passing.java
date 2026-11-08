








class Sample {
  static void log(Object... parts) {}

  void run(String[] words, Object[] objects) {
    log("a");
    log(objects);
    log((Object[]) words);
  }
}
