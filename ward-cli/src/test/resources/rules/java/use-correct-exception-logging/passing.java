











class Sample {
  void run(org.apache.commons.logging.Log log) {
    try {
      work();
    } catch (Exception e) {
      log.error("failed", e);
      log.error(e.getMessage());
    }
    log.error("plain");
  }

  void work() {}
}
