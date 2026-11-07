final class Check {
  Object open(String url, String password) throws Exception {
    return java.sql.DriverManager.getConnection(url, "app", password);
  }
}
