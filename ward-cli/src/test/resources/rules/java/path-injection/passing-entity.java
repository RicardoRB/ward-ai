import java.io.File;
import org.apache.commons.io.FilenameUtils;
import org.springframework.web.bind.annotation.RequestBody;

// Near variants: a type that no handler binds, a getter with an argument, a name that is not a
// getter, a value that is reduced to a file name, and a constant.
class TableController {
  void save(@RequestBody Table table) {}
}

class TableService {
  private final TableMapper mapper = new TableMapper();

  File notBound(String name) {
    Setting setting = mapper.selectSetting(name);
    return new File(setting.getDirectory());
  }

  File withArgument(String name) {
    Table table = mapper.selectByName(name);
    return new File(table.getPath("fixed"));
  }

  File notAGetter(String name) {
    Table table = mapper.selectByName(name);
    return new File(table.path());
  }

  File reduced(String name) {
    Table table = mapper.selectByName(name);
    return new File("/data/" + FilenameUtils.getName(table.getGenPath()));
  }

  File constant() {
    Table table = mapper.selectByName("x");
    return new File("/data/fixed");
  }
}
