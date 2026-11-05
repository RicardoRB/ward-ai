package accuracy.path;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.file.Paths;

class PathJakartaGetPathInfo {
  void read(HttpServletRequest request) {
    String p = request.getPathInfo();
    Paths.get(p);
  }
}
