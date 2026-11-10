import java.util.function.Function;

final class T {
  private static final Function<String, Integer> LEN = s -> s.length();

  int run(String s) {
    return LEN.apply(s);
  }
}
