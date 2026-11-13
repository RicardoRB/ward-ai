import javax.naming.Context;
import javax.servlet.http.HttpServletRequest;

class Resources {
  Object constant(Context context) throws Exception {
    return context.lookup("java:comp/env/jdbc/main");
  }

  Object numeric(HttpServletRequest request, Context context) throws Exception {
    int shard = Integer.parseInt(request.getParameter("shard"));
    return context.lookup("java:comp/env/jdbc/shard" + shard);
  }
}
