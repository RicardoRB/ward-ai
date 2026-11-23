import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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

  // The chain removes both line breaks, whatever expression it starts from.
  void callReceiver(HttpServletRequest request, Logger log) {
    String user = request.getParameter("user");
    log.info(String.valueOf(user).replace("\r", "").replace("\n", ""));
    log.info(java.util.Objects.toString(user).replaceAll("[\\r\\n]", ""));
    log.info(user.toString().replace("\n", "_").replace("\r", "_"));
  }

  void keptInLocal(HttpServletRequest request, Logger log) {
    String user = request.getParameter("user");
    final String safe = String.valueOf(user).replace("\r", "").replace("\n", "");
    log.info("user " + safe);
  }

  // A helper of the project that returns the stripped value.
  void helper(HttpServletRequest request, Logger log) {
    String user = request.getParameter("user");
    log.info("user " + clean(String.valueOf(user)));
  }

  private static String clean(String value) {
    return value.replace("\r", "").replace("\n", "");
  }

  // The request URI is the path as sent, undecoded; a container rejects a raw line break in it.
  void requestUri(HttpServletRequest request, Logger log) {
    log.error("request {}", request.getRequestURI());
  }

  void requestUriLocal(HttpServletRequest request, Logger log) {
    String uri = request.getRequestURI();
    log.warn("request " + uri);
  }

  void requestUriHelper(HttpServletRequest request, Logger log) {
    log.info(describe(request));
    log.info(path(request).trim());
  }

  String describe(HttpServletRequest request) {
    return "path " + request.getRequestURI();
  }

  String path(HttpServletRequest request) {
    return request.getRequestURI();
  }

  // Decoding a constant creates no taint.
  void decodedConstant(Logger log) throws Exception {
    log.info(URLDecoder.decode("constant%20text", StandardCharsets.UTF_8));
  }
}
