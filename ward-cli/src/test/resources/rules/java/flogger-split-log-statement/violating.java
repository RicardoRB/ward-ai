class Sample {
  void run(com.google.common.flogger.FluentLogger logger) {
    com.google.common.flogger.FluentLogger.Api api = logger.atInfo();
    api.log("message");
  }
}
