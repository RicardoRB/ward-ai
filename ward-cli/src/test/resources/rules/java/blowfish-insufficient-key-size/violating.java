final class Check {
  void run() throws Exception {
    KeyGenerator generator = KeyGenerator.getInstance("Blowfish");
    generator.init(64);
  }
}
