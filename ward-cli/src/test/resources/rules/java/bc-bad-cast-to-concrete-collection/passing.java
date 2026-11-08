import java.util.ArrayList;
import java.util.List;

class CastsOk {
  Object good(List<String> items) {
    return (Object) items;
  }

  ArrayList<String> other(Object items) {
    return (ArrayList<String>) items;
  }
}
