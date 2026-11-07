final class Parser {
  void parse(String text) {
    try {
      System.out.println(text);
    } catch (IllegalStateException e) {
      System.out.println(e);
    }
  }
}
