final class Check {
  Object test(java.sql.PreparedStatement ps, String value) {
    return ps.setString(1, value);
  }
}
