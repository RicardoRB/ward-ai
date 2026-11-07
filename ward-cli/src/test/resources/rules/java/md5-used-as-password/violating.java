final class Check {
  void run() throws Exception {
    MessageDigest digest = MessageDigest.getInstance("MD5");
    user.setPassword(digest.digest(raw));
  }
}
