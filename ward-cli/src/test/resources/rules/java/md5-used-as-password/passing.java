final class Check {
  void run() throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    user.setPassword(digest.digest(raw));
  }
}
