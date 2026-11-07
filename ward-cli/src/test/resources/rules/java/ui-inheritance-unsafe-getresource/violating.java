final class Sample {
  Object load() {
    Object resource = getClass().getResource("a.txt");
    return resource;
  }
}
