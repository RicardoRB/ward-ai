import java.util.function.Function;

class Passing {
  void run() {
    Runnable r = this::go;
    Runnable q = () -> {
      go();
    };
    Function<String, Integer> f = s -> s.length() + 1;
  }
  void go() {
  }
}
