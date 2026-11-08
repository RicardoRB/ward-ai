class Sample {
  void run(com.google.common.flogger.FluentLogger logger, String a) {
    logger.atInfo().log("%s and %s", a);
  }
}
