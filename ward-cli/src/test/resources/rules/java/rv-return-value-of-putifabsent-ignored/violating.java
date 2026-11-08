import java.util.Map;

final class Check {
  Object test(Map<String, Object> map, String key) {
    Object value = new Object();
    map.putIfAbsent(key, value);
    return value;
  }
}
