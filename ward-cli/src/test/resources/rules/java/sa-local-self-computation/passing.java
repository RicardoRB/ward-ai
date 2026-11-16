final class Box {
  private int size;

  int mix(int value, int other) {
    return value ^ other;
  }

  boolean[] table() {
    return new boolean[] {true ^ true, false | false, 1 - 1 == 0};
  }
}
