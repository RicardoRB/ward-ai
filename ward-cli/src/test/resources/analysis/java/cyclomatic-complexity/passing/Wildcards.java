import java.util.List;
import java.util.Map;

final class Wildcards {
  int count(Map<?, ?> first, Map<?, ?> second, List<? extends Object> items) {
    return first.size() + second.size() + items.size();
  }
}
