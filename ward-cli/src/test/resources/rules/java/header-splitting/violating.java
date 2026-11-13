import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

class Headers {
  void direct(HttpServletRequest request, HttpServletResponse response) {
    response.setHeader("X-Origin", request.getParameter("origin"));
  }

  void propagated(HttpServletRequest request, HttpServletResponse response) {
    String name = request.getParameter("name");
    String value = "user=" + name;
    response.addHeader("X-User", value);
  }

  // A value that another method of the file returns is followed.
  void crossMethod(HttpServletRequest request, HttpServletResponse response) {
    String origin = read(request);
    response.setHeader("X-Origin", origin);
  }

  String read(HttpServletRequest request) {
    return request.getParameter("origin");
  }
}
