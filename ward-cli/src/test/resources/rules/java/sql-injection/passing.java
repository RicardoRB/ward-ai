import java.io.File;
import java.sql.Connection;
import java.sql.Statement;
import javax.servlet.http.HttpServletRequest;

class Search {
  void run(HttpServletRequest request, Connection connection) throws Exception {
    String id = request.getParameter("id");
    var ps = connection.prepareStatement("select * from t where id = ?");
    ps.setString(1, id);
  }
}
