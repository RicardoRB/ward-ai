import java.util.Collection;
import java.util.List;

class CastsOk {
  Object good(Collection<String> items) {
    return (Object) items;
  }

  List<String> other(Object items) {
    return (List<String>) items;
  }
}
