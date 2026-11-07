final class Sample {
  Object load() {
    Object resource = Sample.class.getResource("a.txt");
    return resource;
  }
}
