package accuracy.sql;

import javax.servlet.http.HttpServletRequest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

class SqlValueOfLongSanitized {
  void search(HttpServletRequest request, Statement statement) throws Exception {
    Long id = Long.valueOf(request.getParameter("id"));
    statement.executeUpdate("update items set seen = 1 where id = " + id);
  }
}
