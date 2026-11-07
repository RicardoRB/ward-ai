package fixture;

import java.io.PrintStream;

final class Reporter {
  void report(final String message) {
    PrintStream sink = System.err;
    sink.println(message);
  }
}
