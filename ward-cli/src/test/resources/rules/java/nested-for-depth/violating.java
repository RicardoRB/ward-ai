final class Loops {
  void run(final int[][][] grid) {
    for (int[][] plane : grid) {
      for (int[] row : plane) {
        for (int cell : row) {
          cell++;
        }
      }
    }
  }
}
