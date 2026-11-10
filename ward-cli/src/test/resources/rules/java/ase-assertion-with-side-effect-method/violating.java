final class Check {
  void drop(java.util.List<String> names) {
    assert names.remove("a");
  }
}
