import java.util.ArrayList;
import java.util.List;
class Lists {
  List<Integer> a(Integer[] ints) {
    List<Integer> l = new ArrayList<>();
    for (int i = 0; i < ints.length; i++) {
      l.add(ints[i].intValue());
    }
    return l;
  }
}
