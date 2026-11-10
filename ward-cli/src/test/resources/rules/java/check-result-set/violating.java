import java.sql.ResultSet;

final class Reader {
  void read(ResultSet rows) throws Exception {
    rows.next();
  }
}
