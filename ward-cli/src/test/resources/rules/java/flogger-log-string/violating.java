class Sample {
  void run(com.google.common.flogger.FluentLogger logger, String text) {
    logger.atInfo().log(text);
  }
}
