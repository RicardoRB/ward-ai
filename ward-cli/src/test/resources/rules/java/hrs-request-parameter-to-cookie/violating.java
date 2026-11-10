final class Check {
  Object cookie(Request request) {
    return new Cookie("name", request.getParameter("value"));
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
