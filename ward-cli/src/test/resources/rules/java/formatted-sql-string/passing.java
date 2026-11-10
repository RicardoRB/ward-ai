final class Check {
  Object use(Object secret, byte[] iv) throws Exception {
    return String.format("hello %s", secret);
  }
}
