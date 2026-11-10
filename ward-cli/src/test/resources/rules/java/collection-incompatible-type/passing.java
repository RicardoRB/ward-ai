final class T {
  boolean run(List<String> names, Map<String, Long> ids, Object any) {
    boolean a = names.contains("a");
    boolean b = ids.containsKey("b");
    return a || b || names.contains(any);
  }
}
