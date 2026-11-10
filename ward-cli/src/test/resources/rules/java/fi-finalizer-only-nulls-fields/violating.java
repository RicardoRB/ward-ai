final class Sample {
  Object buffer;

  protected void finalize() {
    buffer = null;
  }
}
