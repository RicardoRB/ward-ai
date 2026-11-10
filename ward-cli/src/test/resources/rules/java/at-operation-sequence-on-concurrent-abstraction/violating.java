import java.util.concurrent.ConcurrentHashMap;

class Cache {
  private final ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();

  void add(String key) {
    if (!map.containsKey(key)) {
      map.put(key, 1);
    }
  }

  void fetch(String key) {
    if (map.get(key) == null) {
      map.put(key, 2);
    }
  }
}
