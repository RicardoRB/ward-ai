final class Sample {
  void stop() {
    System.runFinalizersOnExit(true);
  }
}
