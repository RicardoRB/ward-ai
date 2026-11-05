package accuracy.path;

import javax.servlet.http.HttpServletRequest;
import java.io.File;

class PathCustomFilesType {
  void open(HttpServletRequest request, Files files) {
    String name = request.getParameter("name");
    files.get(name);
  }
}

class Files {
  void get(String name) {}
}
