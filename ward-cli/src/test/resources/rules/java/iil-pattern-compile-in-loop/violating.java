class Compile {
  void bad(java.util.List<String> xs) {
    for (String x : xs) {
      java.util.regex.Pattern.compile(x);
    }
  }
}
