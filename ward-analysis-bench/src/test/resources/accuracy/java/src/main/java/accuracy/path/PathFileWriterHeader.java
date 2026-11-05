package accuracy.path;

import jakarta.servlet.http.HttpServletRequest;
import java.io.FileWriter;

class PathFileWriterHeader {
  void write(HttpServletRequest request) throws Exception {
    String name = request.getHeader("X-File");
    FileWriter writer = new FileWriter(name);
  }
}
