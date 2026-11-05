package accuracy.path;

import javax.servlet.http.HttpServletRequest;
import java.io.RandomAccessFile;

class PathRandomAccessModeOnly {
  void open(HttpServletRequest request) throws Exception {
    String mode = request.getParameter("mode");
    RandomAccessFile file = new RandomAccessFile("/data/log", mode);
  }
}
