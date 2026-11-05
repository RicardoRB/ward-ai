package accuracy.sql;

import javax.servlet.http.HttpServletRequest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

class SqlParsedIntegerSanitized {
  void search(HttpServletRequest request, Statement statement) throws Exception {
    int id = Integer.parseInt(request.getParameter("id"));
    statement.executeQuery("select * from items where id = " + id);
  }
}
