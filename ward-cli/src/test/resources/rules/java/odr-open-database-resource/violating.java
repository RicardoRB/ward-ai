import java.sql.Connection;

class Sample {
  void query(javax.sql.DataSource source) throws Exception {
    Connection conn = source.getConnection();
    conn.commit();
  }
}

