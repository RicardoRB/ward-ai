import java.util.function.Function;

class Violating {
  void run() {
    Runnable r = () -> go();
    Function<String, Integer> f = s -> s.length();
  }
  void go() {
  }
}
