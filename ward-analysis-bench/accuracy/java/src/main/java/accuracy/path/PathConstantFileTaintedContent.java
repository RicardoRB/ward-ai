package accuracy.path;

import javax.servlet.http.HttpServletRequest;
import java.io.FileReader;

class PathConstantFileTaintedContent {
  void read(HttpServletRequest request) throws Exception {
    String content = request.getParameter("content");
    FileReader reader = new FileReader("/etc/app.conf");
    System.out.println(content + reader);
  }
}
