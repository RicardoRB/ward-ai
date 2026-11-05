package accuracy.path;

import javax.servlet.http.HttpServletRequest;
import java.io.File;

class PathFileConstructorGetParameter {
  void read(HttpServletRequest request) {
    String name = request.getParameter("name");
    File file = new File("/data", name);
  }
}
