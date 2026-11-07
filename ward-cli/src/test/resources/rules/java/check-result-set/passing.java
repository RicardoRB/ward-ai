import java.sql.ResultSet;

final class Reader {
  boolean read(ResultSet rows) throws Exception {
    return rows.next();
  }
}
