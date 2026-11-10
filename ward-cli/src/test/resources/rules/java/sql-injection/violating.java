import java.io.File;
import java.sql.Connection;
import java.sql.Statement;
import javax.servlet.http.HttpServletRequest;

class Search {
  void run(HttpServletRequest request, Statement statement) throws Exception {
    String id = request.getParameter("id");
    statement.executeQuery("select * from t where id = " + id);
  }
}
