import org.slf4j.Logger;

final class Service {
  void run(Logger log, String name) {
    if (log.isDebugEnabled()) {
      log.debug("name " + name);
    }
  }
}
