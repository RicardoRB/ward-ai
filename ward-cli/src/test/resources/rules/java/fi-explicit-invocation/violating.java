final class Sample {
  void run(Object resource) throws Throwable {
    resource.finalize();
  }
}
