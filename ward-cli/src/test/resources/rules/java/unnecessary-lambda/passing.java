import java.util.function.Function;

final class T {
  private static final Function<String, Integer> LEN = s -> s.length();
  public static final Function<String, Integer> OPEN = s -> s.length();

  int run(String s) {
    return LEN.apply(s) + LEN.apply(s);
  }
}
