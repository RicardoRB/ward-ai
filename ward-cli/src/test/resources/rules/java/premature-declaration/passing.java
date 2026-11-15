class Passing {
  int run(String[] strings) {
    int length = 0;
    length += strings.length;
    if (strings == null) {
      return 0;
    }
    return length;
  }
  int other(String[] strings) {
    int n = compute();
    if (strings == null) {
      return 0;
    }
    return n;
  }
  int compute() {
    return 1;
  }
  Object returnsACall(Object root, String pointer) {
    Object current = root;
    if (pointer.isEmpty()) {
      return java.util.Optional.of(root);
    }
    return current;
  }
  int quotedKeyword(String[] strings) {
    int length = 0;
    String text = """
        return 1;
        """;
    length += text.length();
    return length;
  }
}
