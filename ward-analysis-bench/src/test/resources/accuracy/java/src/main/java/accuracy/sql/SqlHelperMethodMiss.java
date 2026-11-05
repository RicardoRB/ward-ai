package accuracy.sql;

import javax.servlet.http.HttpServletRequest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

class SqlHelperMethodMiss {
  void handle(HttpServletRequest request, Statement statement) throws Exception {
    run(statement, request.getParameter("id"));
  }

  private void run(Statement statement, String id) throws Exception {
    statement.executeQuery("select * from items where id = " + id);
  }
}
