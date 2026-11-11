import java.util.List;
import java.util.stream.Stream;
import java.util.function.Supplier;

final class Helpers {
  private void used() {
  }

  private static int convert(final String text) {
    return text.length();
  }

  private String render() {
    return "";
  }

  void run(final List<String> names) {
    used();
    names.stream().map(Helpers::convert).count();
    final Supplier<String> supplier = this::render;
  }

  @Deprecated
  private void legacy() {
  }

  private void readObject(final java.io.ObjectInputStream in) {
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.MethodSource("cases")
  void sourced(final String name) {
  }

  private static Stream<String> cases() {
    return Stream.of("a");
  }
}
