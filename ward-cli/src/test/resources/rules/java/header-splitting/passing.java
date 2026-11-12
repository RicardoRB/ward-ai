import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

class Headers {
  void constant(HttpServletResponse response) {
    response.setHeader("X-Origin", "internal");
  }

  void numeric(HttpServletRequest request, HttpServletResponse response) {
    int version = Integer.parseInt(request.getParameter("version"));
    response.setHeader("X-Version", "v" + version);
  }

  // The limit: a value that leaves the method is not followed.
  void crossMethod(HttpServletRequest request, HttpServletResponse response) {
    String origin = read(request);
    response.setHeader("X-Origin", origin);
  }

  String read(HttpServletRequest request) {
    return request.getParameter("origin");
  }
}
