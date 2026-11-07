final class Sizes {
  boolean empty(java.util.List<String> values) {
    return values.size() < 0;
  }

  boolean never(String[] values) {
    return 0 > values.length();
  }
}
