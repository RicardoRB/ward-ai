import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;
import javax.servlet.http.HttpServletRequest;

class Directory {
  Object direct(HttpServletRequest request, DirContext context) throws Exception {
    return context.search("ou=people", request.getParameter("filter"), new SearchControls());
  }

  Object propagated(HttpServletRequest request, DirContext context) throws Exception {
    String user = request.getParameter("uid");
    String filter = "(uid=" + user + ")";
    return context.search("ou=people", filter, new SearchControls());
  }

  // A value that another method of the file returns is followed.
  Object crossMethod(HttpServletRequest request, DirContext context) throws Exception {
    String filter = read(request);
    return context.search("ou=people", filter, new SearchControls());
  }

  String read(HttpServletRequest request) {
    return request.getParameter("filter");
  }
}
