final class Parser {
  void parse(String text) {
    try {
      throw new WrapperException(text);
    } catch (WrapperException e) {
      System.out.println(e);
    }
  }
}

class WrapperException extends RuntimeException {
  WrapperException(String text) {
    super(text);
  }
}
