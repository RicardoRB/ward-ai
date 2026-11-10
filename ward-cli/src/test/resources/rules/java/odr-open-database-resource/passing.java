import java.sql.Connection;

class Sample {
  void closed(javax.sql.DataSource source) throws Exception {
    Connection conn = source.getConnection();
    conn.commit();
    conn.close();
  }

  Connection handed(javax.sql.DataSource source) throws Exception {
    Connection conn = source.getConnection();
    return conn;
  }
}

