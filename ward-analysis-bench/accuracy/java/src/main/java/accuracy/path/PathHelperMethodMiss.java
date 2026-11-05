package accuracy.path;

import javax.servlet.http.HttpServletRequest;
import java.io.File;

class PathHelperMethodMiss {
  void handle(HttpServletRequest request) {
    open(request.getParameter("name"));
  }

  private File open(String name) {
    return new File(name);
  }
}
