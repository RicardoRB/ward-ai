class Loader {
  Object bad(java.net.URL[] urls) {
    return new java.net.URLClassLoader(urls);
  }
}
