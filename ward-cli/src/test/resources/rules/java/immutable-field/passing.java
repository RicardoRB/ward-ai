final class Counter {
  private int count;
  private int limit;
  private int twice;
  private int spec;
  @picocli.CommandLine.Option private String name = "n";
  @picocli.CommandLine.Parameters private String file = "f";
  private String injected;
  private int other = 1;

  Counter(boolean flag) {
    if (flag) {
      limit = 1;
    }
    twice = 1;
    twice = 2;
  }

  static Counter make(int value) {
    final Counter made = new Counter(true);
    made.other = value;
    return made;
  }

  void increment() {
    count++;
  }

  int value() {
    return count + limit + twice + spec;
  }
}

@lombok.Data
final class Holder {
  private int size = 1;
}
