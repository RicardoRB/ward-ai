import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class Check {
  List<String> test(String name) {
    List<String> names = new ArrayList<>();
    names.add(name);
    return names;
  }

  int distinct(int[] values) {
    final Set<Integer> seen = new HashSet<>();
    int count = 0;
    for (final int value : values) {
      if (seen.add(value)) {
        count++;
      }
    }
    return count;
  }
}
