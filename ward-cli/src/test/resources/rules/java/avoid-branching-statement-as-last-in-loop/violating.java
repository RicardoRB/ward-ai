class Scan {
  void first(int[] values) {
    for (int value : values) {
      if (value > 0) {
        log(value);
      }
      break;
    }
  }

  void log(int value) {}
}
