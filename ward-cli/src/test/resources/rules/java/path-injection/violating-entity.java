import java.io.File;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestBody;

// A type that a handler binds from the request is edited by the user, so the getters of its
// instances are untrusted where a service reads them, also after the entity was stored and loaded
// again (#769). The step is assumed, so the findings are possible.
class TableController {
  void save(@RequestBody Table table) {}

  void edit(@ModelAttribute Draft draft) {}
}

class TableService {
  private final TableMapper mapper = new TableMapper();

  File target(String name) {
    Table table = mapper.selectByName(name);
    return new File(table.getGenPath() + File.separator + "out");
  }

  void write(Draft draft) {
    new File(draft.getDirectory());
  }

  File viaHelper(String name) {
    Table table = mapper.selectByName(name);
    return open(table.getGenPath());
  }

  File open(String path) {
    return new File(path);
  }
}
