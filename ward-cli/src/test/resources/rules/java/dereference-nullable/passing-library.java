package demo;

import java.util.HashMap;
import java.util.Map;

class Library {
  private final Map<String, String> map = new HashMap<>();

  int checkedKey(String key) {
    if (map.containsKey(key)) {
      return map.get(key).length();
    }
    return 0;
  }

  int checkedValue(String key) {
    if (map.get(key) != null) {
      return map.get(key).length();
    }
    return 0;
  }

  int withDefault(Map<String, String> values) {
    return values.getOrDefault("key", "none").length();
  }

  int viaLocal(String key) {
    final String value = map.get(key);
    return value == null ? 0 : value.length();
  }

  int unknownReceiver(Registry registry) {
    return registry.get("key").length();
  }

  interface Registry {
    String get(String key);
  }
}
