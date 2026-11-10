final class Check {
  Object test(java.sql.PreparedStatement ps, String value) {
    return ps.setString(0, value);
  }
}
