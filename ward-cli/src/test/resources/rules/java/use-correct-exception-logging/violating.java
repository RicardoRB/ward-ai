class Sample {
  void run(org.apache.commons.logging.Log log) {
    try {
      work();
    } catch (Exception e) {
      log.error(e);
    }
  }

  void work() {}
}
