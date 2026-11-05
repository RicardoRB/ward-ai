package accuracy.sql;

import javax.servlet.http.HttpServletRequest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

class SqlDigitsOnlyCustomCheck {
  void search(HttpServletRequest request, Statement statement) throws Exception {
    String id = request.getParameter("id").replaceAll("[^0-9]", "");
    statement.executeQuery("select * from items where id = " + id);
  }
}
