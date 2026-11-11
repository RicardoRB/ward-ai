








class Sample {
  static void log(Object... parts) {}

  void run(String[] words, Object[] objects) {
    log("a");
    log(objects);
    log((Object[]) words);
  }

  static <T> void generic(T... items) {}

  void more(String[] words) {
    generic(words);
  }

  static void join(CharSequence... names) {}

  void names(String[] words) {
    join(words);
  }
}
