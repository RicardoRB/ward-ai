import java.sql.Statement;
import org.springframework.web.bind.annotation.RequestParam;

class Check {
  void run(@RequestParam String value, Statement statement) throws Exception {
    statement.executeQuery("select * from t where id = 1");
  }
}
