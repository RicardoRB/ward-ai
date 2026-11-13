import java.io.File;
import java.sql.Connection;
import java.sql.Statement;
import javax.servlet.http.HttpServletRequest;

class Download {
  File find(HttpServletRequest request) {
    String name = request.getParameter("name");
    return new File("/data/" + name);
  }
}

class Report {
  Object write(HttpServletRequest request) throws Exception {
    String name = request.getParameter("name");
    return new java.io.PrintWriter("/data/" + name);
  }
}
