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

  // A value that another method of the file returns is followed.
  void crossMethod(HttpServletRequest request, org.slf4j.Logger log) {
    String user = read(request);
    log.info(user);
  }

  String read(HttpServletRequest request) {
    return request.getParameter("user");
  }
}
