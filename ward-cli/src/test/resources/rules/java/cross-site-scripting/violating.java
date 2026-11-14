import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

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

  // The writer is the result of a call, so its type is not known.
  void chained(HttpServletRequest request, HttpServletResponse response) throws IOException {
    response.getWriter().println("<p>" + request.getParameter("name") + "</p>");
  }
}
