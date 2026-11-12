import javax.servlet.http.HttpServletRequest;
import org.springframework.expression.ExpressionParser;

class Rules {
  Object constant(ExpressionParser parser) {
    return parser.parseExpression("1 + 2").getValue();
  }

  Object numeric(HttpServletRequest request, ExpressionParser parser) {
    int limit = Integer.parseInt(request.getParameter("limit"));
    return parser.parseExpression("#count < " + limit).getValue();
  }

  // The limit: a value that leaves the method is not followed.
  Object crossMethod(HttpServletRequest request, ExpressionParser parser) {
    String rule = read(request);
    return parser.parseExpression(rule).getValue();
  }

  String read(HttpServletRequest request) {
    return request.getParameter("rule");
  }
}
