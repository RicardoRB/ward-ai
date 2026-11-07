class Sample {
  void run(org.slf4j.Logger logger, int a) {
    logger.info("{} and {}", a);
    logger.info("nothing", a);
  }
}
