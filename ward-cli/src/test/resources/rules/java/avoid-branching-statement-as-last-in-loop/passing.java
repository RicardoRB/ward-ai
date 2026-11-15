class Scan {
  void first(int[] values) {
    for (int value : values) {
      if (value > 0) {
        log(value);
        break;
      }
    }
  }

  void log(int value) {}

  void each(int[] values) {
    for (int value : values) {
      returnsOf(value);
    }
  }

  void returnsOf(int value) {}
}
