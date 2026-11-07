final class Violating {
  boolean run(Class<Integer> type, String text) {
    return type.isInstance(text);
  }
}
