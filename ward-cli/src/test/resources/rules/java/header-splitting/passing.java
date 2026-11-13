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
}
