import java.util.function.Consumer;

class Lambda {
  void run() {
    Consumer<String> c = s -> {
      if (s.isEmpty()) {
        return;
      }
      use(s);
    };
    done();
  }
}
