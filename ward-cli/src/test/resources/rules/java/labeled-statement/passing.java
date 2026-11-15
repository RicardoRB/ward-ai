final class Search {
  boolean find(int[][] grid) {
    for (int[] row : grid) {
      for (int cell : row) {
        if (cell == 0) {
          return true;
        }
      }
    }
    return false;
  }
}

final class Refs {
  java.util.function.Predicate<String> empty() {
    return String::isEmpty;
  }
}
