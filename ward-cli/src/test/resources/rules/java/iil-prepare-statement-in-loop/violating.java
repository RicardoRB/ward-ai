class Prepare {
  void bad(java.sql.Connection c, java.util.List<String> xs) throws java.sql.SQLException {
    for (String x : xs) {
      c.prepareStatement(x);
    }
  }
}
