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

  // A number cannot carry a line break, whatever the String it is computed from.
  void count(HttpServletRequest request, HttpServletResponse response) {
    String key = request.getHeader("X-Key");
    long seconds = Math.max(1, key.length());
    response.setHeader("Retry-After", String.valueOf(seconds));
  }
}
