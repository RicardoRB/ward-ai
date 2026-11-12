import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;

class Audit {
  void numeric(HttpServletRequest request, Logger log) {
    int attempt = Integer.parseInt(request.getParameter("attempt"));
    log.info("attempt " + attempt);
  }

  void constant(Logger log) {
    log.info("started");
  }

  // The limit: a value that leaves the method is not followed.
  void crossMethod(HttpServletRequest request, Logger log) {
    String user = read(request);
    log.info(user);
  }

  String read(HttpServletRequest request) {
    return request.getParameter("user");
  }
}
