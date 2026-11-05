package accuracy.path;

import javax.servlet.http.HttpServletRequest;
import java.io.File;

class PathFieldMiss {
  private String name;

  void capture(HttpServletRequest request) {
    this.name = request.getParameter("name");
  }

  File open() {
    return new File(this.name);
  }
}
