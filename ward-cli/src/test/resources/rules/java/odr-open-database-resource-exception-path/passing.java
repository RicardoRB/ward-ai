import java.sql.Connection;

class Sample {
  void guarded(javax.sql.DataSource source) throws Exception {
    Connection conn = source.getConnection();
    try {
      conn.commit();
    } finally {
      conn.close();
    }
  }
}

