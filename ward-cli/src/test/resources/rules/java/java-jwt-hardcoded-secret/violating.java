final class Check {
  Object use(Object secret, byte[] iv) throws Exception {
    return Algorithm.HMAC256("secret");
  }
}
