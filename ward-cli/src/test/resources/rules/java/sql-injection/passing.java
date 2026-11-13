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
