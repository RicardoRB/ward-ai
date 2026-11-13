import java.util.regex.Pattern;
import javax.servlet.http.HttpServletRequest;

class Search {
  Object quoted(HttpServletRequest request) {
    return Pattern.compile(Pattern.quote(request.getParameter("term")));
  }

  Object constant() {
    return Pattern.compile("[a-z]+");
  }
}
