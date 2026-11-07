class CompileOk {
  void good(java.util.List<String> xs) {
    java.util.regex.Pattern p = java.util.regex.Pattern.compile("a");
    for (String x : xs) {
      p.matcher(x);
    }
  }
}
