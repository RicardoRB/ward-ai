final class Check {
  Object cookie(Request request) {
    return new Cookie("name", "fixed");
  }

  static final class Request {
    String getParameter(String name) {
      return name;
    }
  }

  static final class Cookie {
    Cookie(String name, String value) {}
  }
}
