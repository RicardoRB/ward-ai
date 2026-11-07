import org.slf4j.Logger;

final class Service {
  void run(Logger log, String name) {
    log.debug("name " + name);
  }
}
