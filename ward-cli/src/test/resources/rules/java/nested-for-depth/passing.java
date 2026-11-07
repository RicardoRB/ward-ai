final class Loops {
  void run(final int[][] grid) {
    for (int[] row : grid) {
      for (int cell : row) {
        cell++;
      }
    }
  }
}
