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

  void stripped(HttpServletRequest request, Logger log) {
    String user = request.getParameter("user");
    String clean = user.replace("\r", "_").replace("\n", "_");
    log.info("user " + clean);
  }

  void strippedByPattern(HttpServletRequest request, Logger log) {
    String user = request.getParameter("user");
    log.info(user.replaceAll("[\\r\\n]", ""));
    log.info(user.replaceAll("\\R", " "));
  }

  void strippedChars(HttpServletRequest request, Logger log) {
    String user = request.getParameter("user");
    log.info(user.replace('\n', ' ').replace('\r', ' '));
  }
}
