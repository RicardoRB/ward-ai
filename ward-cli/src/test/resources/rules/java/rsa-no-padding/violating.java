final class Check {
  Object use(Object secret, byte[] iv) throws Exception {
    return Cipher.getInstance("RSA/ECB/NoPadding");
  }
}
