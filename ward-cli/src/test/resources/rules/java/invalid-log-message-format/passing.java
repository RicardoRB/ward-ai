






class Sample {
  void run(org.slf4j.Logger logger, int a, Exception e) {
    logger.info("{}", a);
    logger.info("{} failed", a, e);
    logger.info("plain");
  }
}
