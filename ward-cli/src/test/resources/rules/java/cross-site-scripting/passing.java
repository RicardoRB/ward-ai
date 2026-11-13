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
}
