import javax.servlet.http.HttpServletRequest;

class Audit {
  void direct(HttpServletRequest request, java.util.logging.Logger log) {
    log.info(request.getParameter("user"));
  }

  void propagated(HttpServletRequest request, org.slf4j.Logger log) {
    String user = request.getParameter("user");
    String line = "login by " + user;
    log.warn(line);
  }
}
