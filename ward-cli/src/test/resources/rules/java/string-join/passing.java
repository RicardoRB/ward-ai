final class T {
  String run(String a, List<String> list) {
    // ok
    String x = String.join(",", list);
    return String.join(",", a, a);
  }
}
