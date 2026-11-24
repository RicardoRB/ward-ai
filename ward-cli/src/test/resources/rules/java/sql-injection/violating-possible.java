import java.sql.Statement;
import javax.servlet.http.HttpServletRequest;

class Overloads {
  void run(HttpServletRequest request, Statement statement) throws Exception {
    String id = request.getParameter("id");
    String where = condition(id);
    statement.executeQuery("select * from t where " + where);
  }

  String condition(String id) {
    return "id = " + id;
  }

  String condition(Object id) {
    return "id = 0";
  }
}
