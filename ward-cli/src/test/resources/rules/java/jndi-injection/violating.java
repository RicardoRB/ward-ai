import javax.naming.Context;
import javax.servlet.http.HttpServletRequest;

class Resources {
  Object direct(HttpServletRequest request, Context context) throws Exception {
    return context.lookup(request.getParameter("name"));
  }

  Object propagated(HttpServletRequest request, Context context) throws Exception {
    String name = request.getParameter("name");
    String full = "java:comp/env/" + name;
    return context.lookup(full);
  }
}
