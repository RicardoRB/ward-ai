final class Check {
  Object open(String url) throws Exception {
    return java.sql.DriverManager.getConnection(url, "app", "");
  }
}
