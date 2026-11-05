package accuracy.sql;

import javax.servlet.http.HttpServletRequest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

class SqlParameterValuesUpdate {
  void purge(HttpServletRequest request, Statement statement) throws Exception {
    String[] ids = request.getParameterValues("id");
    statement.executeUpdate("delete from items where id in (" + String.join(",", ids) + ")");
  }
}
