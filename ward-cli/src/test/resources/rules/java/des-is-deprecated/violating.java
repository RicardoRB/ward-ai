final class Check {
  Object use(Object secret, byte[] iv) throws Exception {
    return Cipher.getInstance("DES/CBC/PKCS5Padding");
  }
}
