package accuracy.path;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import org.apache.commons.io.FilenameUtils;

class PathFilenameUtilsGetName {
  void read(HttpServletRequest request) {
    String name = request.getParameter("name");
    File file = new File("/data", FilenameUtils.getName(name));
  }
}
