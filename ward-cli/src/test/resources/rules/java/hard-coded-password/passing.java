import java.sql.DriverManager;

class Secrets {
  Object connection() throws Exception {
    return DriverManager.getConnection("jdbc:h2:mem:app", "admin", System.getenv("DB_PASSWORD"));
  }

  void source(javax.sql.DataSource source) {
    source.setPassword(System.getenv("DB_PASSWORD"));
  }
}
