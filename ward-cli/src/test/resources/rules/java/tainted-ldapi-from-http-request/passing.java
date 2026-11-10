import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

class Check {
  void run(HttpServletRequest request, HttpServletResponse response, DirContext context, SearchControls controls) throws Exception {
    String value = request.getParameter("v");
    context.search("ou=people", "(uid=x)", controls);
  }
}
