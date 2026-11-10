final class Hashes {
  boolean stop(Thread worker) {
    return worker.isInterrupted();
  }
}
