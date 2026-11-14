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

  // The literal reaches the password through a variable.
  Object variable() throws Exception {
    String password = "hunter2";
    return DriverManager.getConnection("jdbc:h2:mem:app", "admin", password);
  }
}
