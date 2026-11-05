package accuracy.sql;

import javax.servlet.http.HttpServletRequest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

class SqlOverwrittenBeforeSink {
  void search(HttpServletRequest request, Statement statement) throws Exception {
    String id = request.getParameter("id");
    id = "42";
    statement.executeQuery("select * from items where id = " + id);
  }
}
