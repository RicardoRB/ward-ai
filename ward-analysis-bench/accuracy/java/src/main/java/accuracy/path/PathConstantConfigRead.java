package accuracy.path;

import jakarta.servlet.http.HttpServletRequest;
import java.io.FileInputStream;

class PathConstantConfigRead {
  void read(HttpServletRequest request) throws Exception {
    System.out.println(request.getHeader("X-Trace"));
    FileInputStream in = new FileInputStream("/var/app/config.properties");
  }
}
