import javax.naming.Context
import javax.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.RequestParam

class Subject {
  fun direct(request: HttpServletRequest, context: Context) {
    context.lookup(request.getParameter("x"))
  }

  fun propagated(request: HttpServletRequest, context: Context) {
    val value = request?.getParameter("x") ?: ""
    val text = "<$value>"
    context.lookup(text)
  }

  fun annotated(@RequestParam value: String, context: Context) {
    context.lookup("[${value.trim()}]")
  }

  fun crossMethod(request: HttpServletRequest, context: Context) {
    val value = read(request)
    context.lookup(value)
  }

  fun read(request: HttpServletRequest): String? = request.getParameter("x")
}
