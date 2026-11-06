import java.io.File;
import java.sql.Connection;
import java.sql.Statement;
import javax.servlet.http.HttpServletRequest;

class Download {
  File find(HttpServletRequest request) {
    String name = request.getParameter("name");
    return new File("/data/report.txt");
  }
}
