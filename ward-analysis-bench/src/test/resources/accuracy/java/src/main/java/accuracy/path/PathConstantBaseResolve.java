package accuracy.path;

import javax.servlet.http.HttpServletRequest;
import java.nio.file.Path;
import java.nio.file.Paths;

class PathConstantBaseResolve {
  void open(HttpServletRequest request) {
    Path base = Paths.get("/static");
    String name = request.getParameter("name");
    System.out.println(name);
    base.resolve("index.html");
  }
}
