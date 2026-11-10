final class Check {
  void echo(Response response, Request request) {
    response.setHeader("name", "fixed");
  }

  interface Request {
    String getParameter(String name);
  }

  interface Response {
    void setHeader(String name, String value);
  }
}
