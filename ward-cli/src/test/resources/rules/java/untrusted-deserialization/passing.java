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
}
