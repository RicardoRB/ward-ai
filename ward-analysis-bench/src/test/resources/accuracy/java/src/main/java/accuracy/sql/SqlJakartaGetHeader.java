package accuracy.sql;

import jakarta.servlet.http.HttpServletRequest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

class SqlJakartaGetHeader {
  void lookup(HttpServletRequest request, Statement statement) throws Exception {
    String tenant = request.getHeader("X-Tenant");
    statement.execute("select * from tenants where name = '" + tenant + "'");
  }
}
