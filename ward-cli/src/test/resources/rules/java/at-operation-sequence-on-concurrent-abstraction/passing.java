import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

class Cache {
  private final ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Integer> other = new ConcurrentHashMap<>();

  void add(String key) {
    map.putIfAbsent(key, 1);
  }

  void viaInterface(String key) {
    if (!other.containsKey(key)) {
      other.put(key, 1);
    }
  }

  void unrelated(String key) {
    if (key.isEmpty()) {
      map.put(key, 1);
    }
  }
}
