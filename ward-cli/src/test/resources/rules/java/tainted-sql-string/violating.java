import java.sql.Statement;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

class Check {
  void run(HttpServletRequest request, HttpServletResponse response, Statement statement) throws Exception {
    String value = request.getParameter("v");
    statement.executeQuery("select * from t where id = " + value);
  }
}
