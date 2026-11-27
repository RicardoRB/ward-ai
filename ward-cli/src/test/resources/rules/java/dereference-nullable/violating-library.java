package demo;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

class Library {
  private final Map<String, String> map = new HashMap<>();

  int fromMap(String key) {
    return map.get(key).length();
  }

  String fromProperty() {
    return System.getProperty("app.name").trim();
  }

  String fromEnvironment() {
    return System.getenv("HOME").trim();
  }

  int fromDefault(Map<String, String> values) {
    return values.getOrDefault("key", null).length();
  }

  String fromResource() {
    return Library.class.getResource("a.txt").getPath();
  }

  String fromThrowable(Exception failure) {
    return failure.getMessage().trim();
  }

  Object fromReference(WeakReference<Object> reference) {
    return reference.get().hashCode();
  }
}
