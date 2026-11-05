package accuracy.path;

import javax.servlet.http.HttpServletRequest;
import java.nio.file.Path;
import java.nio.file.Paths;

class PathResolveGetParameter {
  void read(HttpServletRequest request) {
    Path base = Paths.get("/data");
    String name = request.getParameter("name");
    base.resolve(name);
  }
}
