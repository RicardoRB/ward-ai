
import java.util.Map;

class Sample {
  void f(Map<String, Holder> m, String id) {
    m.computeIfAbsent(id, Holder::new);
    m.computeIfAbsent("a", k -> new Holder());
    m.computeIfAbsent("b", Other::new);
    m.computeIfAbsent("c", Third::new);
  }
}
