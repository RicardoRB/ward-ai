import java.sql.DriverManager;
import javax.crypto.spec.PBEKeySpec;

class Secrets {
  Object connection() throws Exception {
    return DriverManager.getConnection("jdbc:h2:mem:app", "admin", "hunter2");
  }

  void source(javax.sql.DataSource source) {
    source.setPassword("hunter2");
  }

  Object spec() {
    return new PBEKeySpec("hunter2".toCharArray());
  }
}
