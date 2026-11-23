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

class Report {
  Object write(HttpServletRequest request) throws Exception {
    String name = request.getParameter("name");
    return new java.io.PrintWriter("/data/report.txt");
  }
}

class Temp {
  // The JVM sets these properties from the operating system, not from a request.
  File home() {
    return new File(System.getProperty("user.home") + "/.app/tmp");
  }

  File local() {
    String location = System.getProperty("java.io.tmpdir") + "/app";
    return new File(location);
  }

  File withDefault() {
    return new File(System.getProperty("user.dir", "."), "data");
  }

  File helper() {
    return new File(base(), "cache");
  }

  String base() {
    return System.getProperty("user.home");
  }
}
