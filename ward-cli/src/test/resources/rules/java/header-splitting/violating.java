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
}
