class Lambdas {
  void run() {
    Runnable first = () -> { };
    Function<String, String> up = value -> value;
    BiFunction<Integer, Integer, Integer> sum = (a, b) -> a;
  }
}
