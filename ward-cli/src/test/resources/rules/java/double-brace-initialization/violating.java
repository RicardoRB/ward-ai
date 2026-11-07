import java.util.HashMap;
import java.util.Map;

final class Maps {
  Map<String, Integer> counts() {
    return new HashMap<String, Integer>() {{
      put("a", 1);
    }};
  }
}
