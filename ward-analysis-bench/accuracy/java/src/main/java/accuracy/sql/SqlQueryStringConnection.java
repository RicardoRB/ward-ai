package accuracy.sql;

import javax.servlet.http.HttpServletRequest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

class SqlQueryStringConnection {
  void report(HttpServletRequest request, Connection connection) throws Exception {
    String filter = request.getQueryString();
    connection.prepareStatement("select * from report where " + filter);
  }
}
