class Logs {
  void bad() {
    java.util.logging.Logger.getLogger("a").setLevel(java.util.logging.Level.ALL);
  }
}
