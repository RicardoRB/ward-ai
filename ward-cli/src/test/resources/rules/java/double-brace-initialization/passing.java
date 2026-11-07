import java.util.HashMap;
import java.util.Map;

final class Maps {
  Map<String, Integer> counts() {
    Map<String, Integer> counts = new HashMap<>();
    counts.put("a", 1);
    return counts;
  }
}
