import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.web.util.HtmlUtils;
import org.springframework.http.MediaType;

class Greeting {
  private final String label;

  // A constructor that calls this(...) and super-like calls has no named callee.
  Greeting() {
    this("hello");
  }

  Greeting(String label) {
    this.label = label;
  }

  void escaped(HttpServletRequest request, PrintWriter out) {
    String name = HtmlUtils.htmlEscape(request.getParameter("name"));
    out.println(name);
  }

  void constant(PrintWriter out) {
    out.println("<h1>Hello</h1>");
  }

  void chained(HttpServletRequest request, HttpServletResponse response) throws IOException {
    response.getWriter().println(HtmlUtils.htmlEscape(request.getParameter("name")));
  }

  // A JSON response is not rendered as HTML.
  void json(HttpServletRequest request, HttpServletResponse response) throws IOException {
    response.setContentType("application/problem+json");
    response.getWriter().write(request.getParameter("name"));
  }

  void jsonConstant(HttpServletRequest request, HttpServletResponse response) throws IOException {
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.getOutputStream().write(request.getParameter("name").getBytes());
  }

  // A length is a number.
  void count(HttpServletRequest request, PrintWriter out) {
    String name = request.getParameter("name");
    out.println(name.length());
  }
}
