import java.io.File;
import java.sql.Connection;
import java.sql.Statement;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestBody;

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

// A request body or form entity is user input, and so are the values its getters return (#769).
class Generator {
  void body(@RequestBody Table table) {
    new File(table.getGenPath());
  }

  File form(@ModelAttribute Table table) {
    return new File(table.getGenPath() + File.separator + "out");
  }

  void throughHelper(@RequestBody Table table) {
    store(table);
  }

  // The call is the finding: the helper writes a file from its parameter.
  void store(Table table) {
    new File(table.getGenPath() + "/x");
  }

  void throughString(@RequestBody Table table) {
    writeAt(table.getGenPath());
  }

  void writeAt(String path) {
    new File(path);
  }

  void throughField(@RequestBody Table table) {
    String path = table.getGenPath();
    new File(path);
  }
}
