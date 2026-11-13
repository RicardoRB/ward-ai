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
}
