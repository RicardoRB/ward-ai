class Catches {
  void run() {
    try {
      go();
    } catch (java.io.IOException e) {
    } catch (RuntimeException ex) {
    }
  }
}
