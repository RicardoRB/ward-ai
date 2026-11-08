class PrepareOk {
  void good(java.sql.Connection c, java.util.List<String> xs) throws java.sql.SQLException {
    java.sql.PreparedStatement p = c.prepareStatement("a");
    for (String x : xs) {
      p.setString(1, x);
    }
  }
}
