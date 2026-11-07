final class Sample {
  Object buffer;

  protected void finalize() {
    cleanup();
    buffer = null;
  }
}
