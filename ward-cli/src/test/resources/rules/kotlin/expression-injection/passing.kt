import javax.servlet.http.HttpServletRequest
import org.springframework.expression.ExpressionParser
import org.springframework.web.bind.annotation.RequestParam

class Subject {
  fun constant(request: HttpServletRequest, parser: ExpressionParser) {
    val unused = request.getParameter("x")
    parser.parseExpression("fixed")
  }

  fun overwritten(request: HttpServletRequest, parser: ExpressionParser) {
    var value = request.getParameter("x")
    value = "fixed"
    parser.parseExpression(value)
  }
}
