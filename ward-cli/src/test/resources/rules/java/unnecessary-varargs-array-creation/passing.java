import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class Lists {
  List<String> pair() {
    return Arrays.asList("a", "b");
  }

  List<int[]> primitive() {
    return Arrays.asList(new int[] {1, 2});
  }

  void notVarargs(List<int[]> regions) {
    regions.add(new int[] {1, 2});
    new ArrayList<int[]>().add(new int[] {3, 4});
  }

  List<String[][]> element() {
    return Arrays.asList(new String[][] {{"a"}});
  }
}
