class Steps {
  void run() {
    for (double x = 0; x < 1; x += 0.1) {
    }
  }

  float sum(float[][] rows) {
    float total = 0;
    for (final float[] row : rows) {
      for (float value : row) {
        total += value;
      }
    }
    return total;
  }
}
