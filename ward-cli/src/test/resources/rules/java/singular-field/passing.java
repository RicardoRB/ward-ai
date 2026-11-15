final class Scale {
  private int factor;

  void setup() {
    factor = 2;
  }

  int apply(int value) {
    return value * factor;
  }
}

final class Offsets {
  private int offset;
  private boolean seen;

  int next(int length) {
    final int start = offset;
    offset += length;
    return start;
  }

  boolean first() {
    if (!seen) {
      seen = true;
      return true;
    }
    return false;
  }
}

final class Command {
  @picocli.CommandLine.Parameters(index = "0")
  private String agent;

  int run() {
    agent = "x";
    return agent.length();
  }
}

final class Outer {
  private static final class Store {
    private String saved;

    void save(String value) {
      saved = value;
    }
  }

  String read(Store store) {
    return store.saved;
  }
}
