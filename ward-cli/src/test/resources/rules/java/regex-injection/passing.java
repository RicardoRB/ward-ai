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

class Lines {
  // The line separator is set by the JVM, not by a request.
  String[] split(String remarks) {
    return remarks.split(System.getProperty("line.separator"));
  }

  String[] splitLocal(String remarks) {
    String separator = System.getProperty("file.separator");
    return remarks.split(separator);
  }

  String join(String text) {
    return text.replaceAll(System.getProperty("path.separator"), ",");
  }
}
