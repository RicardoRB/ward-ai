final class Search {
  void find(int[][] grid) {
    outer:
    for (int[] row : grid) {
      for (int cell : row) {
        if (cell == 0) {
          break outer;
        }
      }
    }
  }
}
