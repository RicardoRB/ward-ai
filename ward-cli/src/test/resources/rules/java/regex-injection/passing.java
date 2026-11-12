import java.util.regex.Pattern;
import javax.servlet.http.HttpServletRequest;

class Search {
  Object quoted(HttpServletRequest request) {
    return Pattern.compile(Pattern.quote(request.getParameter("term")));
  }

  Object constant() {
    return Pattern.compile("[a-z]+");
  }

  // The limit: a value that leaves the method is not followed.
  Object crossMethod(HttpServletRequest request) {
    String pattern = read(request);
    return Pattern.compile(pattern);
  }

  String read(HttpServletRequest request) {
    return request.getParameter("pattern");
  }
}
