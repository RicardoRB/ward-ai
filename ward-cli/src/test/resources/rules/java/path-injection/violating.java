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

class Properties {
  // A key that is not constant, or a property the deployment defines, is not trusted.
  File byName(String key) {
    return new File(System.getProperty(key));
  }

  File custom() {
    return new File(System.getProperty("app.upload.dir") + "/tmp");
  }

  File fromRequest(HttpServletRequest request) {
    return new File(System.getProperty("user.home"), request.getParameter("name"));
  }
}
