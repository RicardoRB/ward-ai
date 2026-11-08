class LogsOk {
  private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger("a");

  void good() {
    LOG.setLevel(java.util.logging.Level.ALL);
  }
}
