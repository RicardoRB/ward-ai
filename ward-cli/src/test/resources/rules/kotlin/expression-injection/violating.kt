import javax.servlet.http.HttpServletRequest
import org.springframework.expression.ExpressionParser
import org.springframework.web.bind.annotation.RequestParam

class Subject {
  fun direct(request: HttpServletRequest, parser: ExpressionParser) {
    parser.parseExpression(request.getParameter("x"))
  }

  fun propagated(request: HttpServletRequest, parser: ExpressionParser) {
    val value = request?.getParameter("x") ?: ""
    val text = "<$value>"
    parser.parseExpression(text)
  }

  fun annotated(@RequestParam value: String, parser: ExpressionParser) {
    parser.parseExpression("[${value.trim()}]")
  }

  fun crossMethod(request: HttpServletRequest, parser: ExpressionParser) {
    val value = read(request)
    parser.parseExpression(value)
  }

  fun read(request: HttpServletRequest): String? = request.getParameter("x")
}
