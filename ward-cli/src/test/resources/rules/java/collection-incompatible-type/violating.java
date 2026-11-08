final class T {
  boolean run(List<String> names, Map<String, Long> ids) {
    return names.contains(1) || ids.containsKey(2L);
  }
}
