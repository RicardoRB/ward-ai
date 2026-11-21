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

  // An HTML content type is rendered.
  void html(HttpServletRequest request, HttpServletResponse response) throws IOException {
    response.setContentType("text/html");
    response.getWriter().write(request.getParameter("name"));
  }

  // One content type that is not JSON is enough.
  void mixed(HttpServletRequest request, HttpServletResponse response) throws IOException {
    response.setContentType("application/json");
    response.setContentType("text/html");
    response.getOutputStream().write(request.getParameter("name").getBytes());
  }
}
