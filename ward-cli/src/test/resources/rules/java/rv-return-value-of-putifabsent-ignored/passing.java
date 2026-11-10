import java.util.Map;

final class Check {
  Object test(Map<String, Object> map, String key) {
    Object value = new Object();
    Object old = map.putIfAbsent(key, value);
    return old == null ? value : old;
  }

  void ignored(Map<String, Object> map, String key, Object value) {
    map.putIfAbsent(key, value);
  }
}
