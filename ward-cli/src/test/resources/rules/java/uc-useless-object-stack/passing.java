import java.util.ArrayList;
import java.util.List;

final class Check {
  String test(List<String> list, String name) {
    list.add(name);
    return new StringBuilder().append(name).toString();
  }
}
