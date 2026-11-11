public class Plain {
  private final int size;

  public Plain() {
    this(1);
  }

  public Plain(int size) {
    this.size = size;
  }
}

class Hidden {
  private Hidden() {}
}

enum Level {
  LOW(1);

  Level(int value) {}
}
