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

  // A value that another method of the file returns is followed.
  Object crossMethod(HttpServletRequest request, Context context) throws Exception {
    String name = read(request);
    return context.lookup(name);
  }

  String read(HttpServletRequest request) {
    return request.getParameter("name");
  }
}
