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

  void onlyLineFeeds(HttpServletRequest request, org.slf4j.Logger log) {
    String user = request.getParameter("user");
    log.info(user.replace("\n", "_"));
  }

  void pairOnly(HttpServletRequest request, org.slf4j.Logger log) {
    String user = request.getParameter("user");
    log.info(user.replace("\r\n", "_"));
  }

  void groupBack(HttpServletRequest request, org.slf4j.Logger log) {
    String user = request.getParameter("user");
    log.info(user.replaceAll("([\\r\\n])", "$1"));
  }

  void callReceiverLineFeedsOnly(HttpServletRequest request, org.slf4j.Logger log) {
    String user = request.getParameter("user");
    log.info(String.valueOf(user).replace("\n", "_"));
  }

  // The helper does not remove the line breaks.
  void helperKeeps(HttpServletRequest request, org.slf4j.Logger log) {
    String user = request.getParameter("user");
    log.info(echo(user));
  }

  void helperLineFeedsOnly(HttpServletRequest request, org.slf4j.Logger log) {
    String user = request.getParameter("user");
    log.info(lineFeeds(user));
  }

  String echo(String value) {
    return value.trim();
  }

  String lineFeeds(String value) {
    return value.replace("\n", "_");
  }
}
