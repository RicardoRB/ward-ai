package accuracy.sql;

import javax.servlet.http.HttpServletRequest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

class SqlFieldMiss {
  private String filter;

  void capture(HttpServletRequest request) {
    this.filter = request.getParameter("filter");
  }

  void run(Statement statement) throws Exception {
    statement.executeQuery("select * from items where " + this.filter);
  }
}
