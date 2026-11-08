import java.util.concurrent.atomic.AtomicReference;

class Worker {
  private StringBuilder log;
  private final AtomicReference<String> name = new AtomicReference<>();

  void start() {
    new Thread(() -> {
      synchronized (this) {
        log = new StringBuilder();
      }
      show();
      name.set("a");
    }).start();
  }

  synchronized void show() {
    log.append("x");
    name.get();
  }

  void outside() {
    log = null;
    log.append("y");
  }
}
