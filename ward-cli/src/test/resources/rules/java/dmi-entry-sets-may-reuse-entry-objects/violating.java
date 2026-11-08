import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class Entries {
  List<Map.Entry<String, Integer>> bad(Map<String, Integer> map) {
    List<Map.Entry<String, Integer>> list = new ArrayList<>();
    for (Map.Entry<String, Integer> entry : map.entrySet()) {
      list.add(entry);
    }
    return list;
  }
}
