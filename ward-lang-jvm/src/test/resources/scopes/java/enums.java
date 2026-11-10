enum Color {
  RED(1), GREEN(2);

  private final int code;

  Color(int code) {
    this.code = code;
  }

  int code() {
    return code + RED.code;
  }
}

interface Shape {
  int SIDES = 4;

  default int sides() {
    return SIDES;
  }
}

record Point(int x, int y) {
  int sum() {
    return x + y;
  }
}
