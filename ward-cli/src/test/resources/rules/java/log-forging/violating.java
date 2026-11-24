import java.net.URLDecoder;
import org.springframework.web.util.UriUtils;
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

  // These parts of the request can hold a line break.
  void queryString(HttpServletRequest request, org.slf4j.Logger log) {
    log.info(request.getQueryString());
  }

  void pathInfo(HttpServletRequest request, org.slf4j.Logger log) {
    log.info("path {}", request.getPathInfo());
  }

  // Decoding keeps the taint of the query string.
  void decodedQuery(HttpServletRequest request, org.slf4j.Logger log) throws Exception {
    String query = request.getQueryString();
    log.info(URLDecoder.decode(query, "UTF-8"));
  }

  void decodedQueryDirect(HttpServletRequest request, org.slf4j.Logger log) throws Exception {
    String path = URLDecoder.decode(request.getQueryString(), "UTF-8");
    log.warn("request " + path);
  }

  // Percent-decoding the request URI can produce a line break (%0A).
  void decodedUri(HttpServletRequest request, org.slf4j.Logger log) throws Exception {
    log.info(URLDecoder.decode(request.getRequestURI(), "UTF-8"));
  }

  void decodedUriSpring(HttpServletRequest request, org.slf4j.Logger log) {
    log.info("path {}", UriUtils.decode(request.getRequestURI(), "UTF-8"));
  }
}
