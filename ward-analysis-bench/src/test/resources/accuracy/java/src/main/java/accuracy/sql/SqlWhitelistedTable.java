package accuracy.sql;

import javax.servlet.http.HttpServletRequest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

class SqlWhitelistedTable {
  void search(HttpServletRequest request, Statement statement) throws Exception {
    String table = request.getParameter("table");
    String safe = "items".equals(table) ? "items" : "orders";
    statement.executeQuery("select * from " + safe);
  }
}
