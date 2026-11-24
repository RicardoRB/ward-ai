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

class Orm {
  void hibernate(HttpServletRequest request, org.hibernate.Session session) {
    String name = request.getParameter("name");
    session.createQuery("from User where name = :name").setParameter("name", name);
  }

  void jooq(HttpServletRequest request, org.jooq.DSLContext create) {
    String id = request.getParameter("id");
    create.fetch("select * from t where id = ?", id);
  }
}

/** The overload that a call reaches is not decided, so the flow is possible and not reported. */
class Overloads {
  void run(HttpServletRequest request, Statement statement) throws Exception {
    String id = request.getParameter("id");
    String where = condition(id);
    statement.executeQuery("select * from t where " + where);
  }

  String condition(String id) {
    return "id = " + id;
  }

  String condition(Object id) {
    return "id = 0";
  }
}
