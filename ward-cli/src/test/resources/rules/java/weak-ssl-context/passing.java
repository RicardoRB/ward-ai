final class Check {
  Object use(Object secret, byte[] iv) throws Exception {
    return SSLContext.getInstance("TLSv1.3");
  }
}
