





class Sample {
  static final String MESSAGE = "constant";

  void run(com.google.common.flogger.FluentLogger logger, String text) {
    logger.atInfo().log("literal");
    logger.atInfo().log("%s", text);
    logger.atInfo().log(MESSAGE);
  }
}
