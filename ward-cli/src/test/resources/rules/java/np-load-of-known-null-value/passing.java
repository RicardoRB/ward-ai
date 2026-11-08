class Sample {
  String run(String text) {
    if (text == null) {
      text = "none";
      log(text);
    }
    if (text != null) {
      log(text);
    }
    return text;
  }

  void log(String value) {}
}

