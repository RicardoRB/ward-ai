import javax.servlet.http.HttpServletRequest;
import org.springframework.expression.ExpressionParser;

class Rules {
  Object direct(HttpServletRequest request, ExpressionParser parser) {
    return parser.parseExpression(request.getParameter("rule")).getValue();
  }

  Object propagated(HttpServletRequest request, ExpressionParser parser) {
    String field = request.getParameter("field");
    String text = "#user." + field;
    return parser.parseExpression(text).getValue();
  }

  // A value that another method of the file returns is followed.
  Object crossMethod(HttpServletRequest request, ExpressionParser parser) {
    String rule = read(request);
    return parser.parseExpression(rule).getValue();
  }

  String read(HttpServletRequest request) {
    return request.getParameter("rule");
  }
}
