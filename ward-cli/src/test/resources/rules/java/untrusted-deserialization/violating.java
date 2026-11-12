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
}
