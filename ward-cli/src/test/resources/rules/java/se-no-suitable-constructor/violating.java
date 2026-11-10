import java.io.Serializable;

class Base {
  Base(int x) {}
}

final class Check extends Base implements Serializable {
  Check() {
    super(1);
  }
}
