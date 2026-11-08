import java.util.Map;

class Sample {
  void f(Map<String, Holder> m, String id) {
    m.computeIfAbsent("a", Holder::new);
    m.computeIfAbsent(id.trim(), Holder::new);
  }
}
