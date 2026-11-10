class Sizes {
  private int base = 1;

  int total(int bound) {
    return base + bound + missing;
  }

  int other() {
    return absent;
  }
}
