class Loaders {
  ClassLoader loader() {
    return Thread.currentThread().getContextClassLoader();
  }
}
