import java.io.BufferedReader;
import java.sql.DriverManager;

class Secrets {
  Object connection() throws Exception {
    return DriverManager.getConnection("jdbc:h2:mem:app", "admin", System.getenv("DB_PASSWORD"));
  }

  void source(javax.sql.DataSource source) {
    source.setPassword(System.getenv("DB_PASSWORD"));
  }

  Object read(BufferedReader reader) throws Exception {
    String password = reader.readLine();
    return DriverManager.getConnection("jdbc:h2:mem:app", "admin", password);
  }

  Object name() throws Exception {
    String variable = "DB_PASSWORD";
    return DriverManager.getConnection("jdbc:h2:mem:app", "admin", System.getenv(variable));
  }
}
