class Sample {
  int checked(@Nullable String text) {
    if (text == null) {
      return 0;
    }
    return text.length();
  }

  int plain(String text) {
    return text.length();
  }

  String passed(@Nullable String text) {
    return text;
  }
}

