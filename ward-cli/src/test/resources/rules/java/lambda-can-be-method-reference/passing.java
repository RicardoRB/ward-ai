import java.util.function.Function;

class Passing {
  void run() {
    Runnable r = this::go;
    Runnable q = () -> {
      go();
    };
    Function<String, Integer> f = s -> s.length() + 1;
    Function<String, StringBuilder> g = s -> new StringBuilder(s);
    java.util.function.Supplier<StringBuilder> h = () -> new StringBuilder();
  }
  void go() {
  }
}
