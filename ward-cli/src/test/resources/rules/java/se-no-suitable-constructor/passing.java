import java.io.Serializable;

class Base {
  Base() {}

  Base(int x) {}
}

final class Check extends Base implements Serializable {
  Check() {
    super(1);
  }
}

class Other extends Elsewhere implements Serializable {}
