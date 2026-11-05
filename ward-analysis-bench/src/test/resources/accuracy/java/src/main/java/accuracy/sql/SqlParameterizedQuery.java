package accuracy.sql;

import javax.servlet.http.HttpServletRequest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

class SqlParameterizedQuery {
  void search(HttpServletRequest request, Connection connection) throws Exception {
    String id = request.getParameter("id");
    PreparedStatement statement = connection.prepareStatement("select * from items where id = ?");
    statement.setString(1, id);
  }
}
