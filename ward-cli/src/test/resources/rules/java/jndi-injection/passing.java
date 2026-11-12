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

  // The limit: a value that leaves the method is not followed.
  Object crossMethod(HttpServletRequest request, Context context) throws Exception {
    String name = read(request);
    return context.lookup(name);
  }

  String read(HttpServletRequest request) {
    return request.getParameter("name");
  }
}
