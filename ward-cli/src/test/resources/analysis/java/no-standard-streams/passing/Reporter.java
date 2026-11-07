package fixture;

import java.io.PrintStream;

final class Reporter {
  private final PrintStream sink;

  Reporter(final PrintStream sink) {
    this.sink = sink;
  }

  void report(final String message) {
    sink.println(message);
  }
}
