final class Check {
  Class<?> load(String name) throws ClassNotFoundException {
    return Class.forName(name);
  }
}
