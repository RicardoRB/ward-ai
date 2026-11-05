package accuracy.path;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import java.io.FileInputStream;

class PathSpringRequestParamStream {
  void read(@RequestParam String name) throws Exception {
    FileInputStream in = new FileInputStream(name);
  }
}
