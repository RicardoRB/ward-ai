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
}
