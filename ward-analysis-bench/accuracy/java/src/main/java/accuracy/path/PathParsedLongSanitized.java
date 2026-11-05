package accuracy.path;

import javax.servlet.http.HttpServletRequest;
import java.io.File;

class PathParsedLongSanitized {
  void read(HttpServletRequest request) {
    long id = Long.parseLong(request.getParameter("id"));
    File file = new File("/data/" + id + ".txt");
  }
}
