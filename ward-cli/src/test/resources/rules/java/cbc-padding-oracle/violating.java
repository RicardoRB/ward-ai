final class Check {
  Object use(Object secret, byte[] iv) throws Exception {
    return Cipher.getInstance("AES/CBC/PKCS5Padding");
  }
}
