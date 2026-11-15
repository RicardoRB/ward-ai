import java.util.ArrayList;
import java.util.List;
class Loops {
  private final int[][] grid = new int[3][];

  void exits(int n) {
    for (int i = 0; i < n; i++) {
      if (i > n) {
        throw new IllegalStateException("too far");
      }
    }
  }

  Object found(int n) {
    for (int i = 0; i < n; i++) {
      if (i == 1) {
        return new Object();
      }
    }
    return null;
  }

  void stops(int n) {
    while (n > 0) {
      Object once = new Object();
      break;
    }
  }

  void fills(int n, List<Object> sink) {
    for (int i = 0; i < n; i++) {
      grid[i] = new int[n];
      sink.add(new Object());
    }
  }

  void outside(int n) {
    for (List<String> names = new ArrayList<>(); n > 0; n--) {
      names.size();
    }
    for (int each : new int[] {1, 2}) {
      n += each;
    }
  }
}
