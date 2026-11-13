import java.io.ObjectInputStream;
import javax.servlet.http.HttpServletRequest;
import org.yaml.snakeyaml.Yaml;

class Import {
  Object direct(HttpServletRequest request) throws Exception {
    return new ObjectInputStream(request.getInputStream()).readObject();
  }

  Object propagated(HttpServletRequest request, Yaml yaml) throws Exception {
    String document = request.getParameter("config");
    return yaml.load(document);
  }

  // A value that another method of the file returns is followed.
  Object crossMethod(HttpServletRequest request, Yaml yaml) {
    String document = read(request);
    return yaml.load(document);
  }

  String read(HttpServletRequest request) {
    return request.getParameter("config");
  }
}
