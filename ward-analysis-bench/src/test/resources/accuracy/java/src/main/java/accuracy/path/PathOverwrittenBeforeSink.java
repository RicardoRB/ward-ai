package accuracy.path;

import javax.servlet.http.HttpServletRequest;
import java.io.File;

class PathOverwrittenBeforeSink {
  void open(HttpServletRequest request) {
    String name = request.getParameter("name");
    name = "default.txt";
    File file = new File("/data", name);
  }
}
