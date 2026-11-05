package accuracy.sql;

import javax.servlet.http.HttpServletRequest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

class SqlServletGetParameter {
  void search(HttpServletRequest request, Statement statement) throws Exception {
    String id = request.getParameter("id");
    statement.executeQuery("select * from items where id = " + id);
  }
}
