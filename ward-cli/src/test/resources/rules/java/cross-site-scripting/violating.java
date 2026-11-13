import java.io.PrintWriter;
import javax.servlet.http.HttpServletRequest;

class Greeting {
  void direct(HttpServletRequest request, PrintWriter out) {
    out.println(request.getParameter("name"));
  }

  void propagated(HttpServletRequest request, PrintWriter out) {
    String name = request.getParameter("name");
    String page = "<h1>" + name + "</h1>";
    out.write(page);
  }

  // A value that another method of the file returns is followed.
  void crossMethod(HttpServletRequest request, PrintWriter out) {
    String name = read(request);
    out.println(name);
  }

  String read(HttpServletRequest request) {
    return request.getParameter("name");
  }
}
