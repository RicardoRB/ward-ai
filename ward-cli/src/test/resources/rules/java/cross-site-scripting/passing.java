import java.io.PrintWriter;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.util.HtmlUtils;

class Greeting {
  void escaped(HttpServletRequest request, PrintWriter out) {
    String name = HtmlUtils.htmlEscape(request.getParameter("name"));
    out.println(name);
  }

  void constant(PrintWriter out) {
    out.println("<h1>Hello</h1>");
  }

  // The limit: a value that leaves the method is not followed.
  void crossMethod(HttpServletRequest request, PrintWriter out) {
    String name = read(request);
    out.println(name);
  }

  String read(HttpServletRequest request) {
    return request.getParameter("name");
  }
}
