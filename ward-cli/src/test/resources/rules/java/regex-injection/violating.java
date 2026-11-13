import java.util.regex.Pattern;
import javax.servlet.http.HttpServletRequest;

class Search {
  Object direct(HttpServletRequest request) {
    return Pattern.compile(request.getParameter("pattern"));
  }

  boolean propagated(HttpServletRequest request, String text) {
    String term = request.getParameter("term");
    String expression = ".*" + term + ".*";
    return text.matches(expression);
  }

  // A value that another method of the file returns is followed.
  Object crossMethod(HttpServletRequest request) {
    String pattern = read(request);
    return Pattern.compile(pattern);
  }

  String read(HttpServletRequest request) {
    return request.getParameter("pattern");
  }
}
