import java.io.ObjectInputStream;
import javax.servlet.http.HttpServletRequest;
import org.yaml.snakeyaml.Yaml;

class Import {
  Object constant(Yaml yaml) {
    return yaml.load("name: demo");
  }

  Object fromFile(java.io.InputStream stored) throws Exception {
    return new ObjectInputStream(stored).readObject();
  }

  // The limit: a value that leaves the method is not followed.
  Object crossMethod(HttpServletRequest request, Yaml yaml) {
    String document = read(request);
    return yaml.load(document);
  }

  String read(HttpServletRequest request) {
    return request.getParameter("config");
  }
}
