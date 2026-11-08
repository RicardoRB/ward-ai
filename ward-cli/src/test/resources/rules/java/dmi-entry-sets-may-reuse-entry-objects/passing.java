import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class EntriesOk {
  List<String> good(Map<String, Integer> map) {
    List<String> keys = new ArrayList<>();
    for (Map.Entry<String, Integer> entry : map.entrySet()) {
      keys.add(entry.getKey());
    }
    for (String key : map.keySet()) {
      keys.add(key);
    }
    return keys;
  }
}
