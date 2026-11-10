import java.util.Collection;
import java.util.List;

class Casts {
  List<String> bad(Collection<String> items) {
    return (List<String>) items;
  }
}
