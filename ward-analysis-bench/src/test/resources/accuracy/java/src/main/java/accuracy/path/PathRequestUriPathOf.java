package accuracy.path;

import javax.servlet.http.HttpServletRequest;
import java.nio.file.Path;

class PathRequestUriPathOf {
  void read(HttpServletRequest request) {
    String uri = request.getRequestURI();
    Path.of(uri);
  }
}
