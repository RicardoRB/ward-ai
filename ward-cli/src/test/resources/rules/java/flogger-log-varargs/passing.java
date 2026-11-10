





class Sample {
  void run(com.google.common.flogger.FluentLogger logger, Object a, Object b) {
    logger.atInfo().log("%s %s", a, b);
  }
}
