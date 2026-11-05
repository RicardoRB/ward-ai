package accuracy.sql;

import javax.servlet.http.HttpServletRequest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

class SqlConstantQueryLoggedRequest {
  void list(HttpServletRequest request, Statement statement) throws Exception {
    String agent = request.getHeader("User-Agent");
    System.out.println(agent);
    statement.executeQuery("select * from items");
  }
}
