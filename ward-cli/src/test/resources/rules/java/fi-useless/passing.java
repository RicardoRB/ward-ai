final class Sample {
  protected void finalize() throws Throwable {
    cleanup();
    super.finalize();
  }
}
