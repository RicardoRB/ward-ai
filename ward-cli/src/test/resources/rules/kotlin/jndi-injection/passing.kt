import javax.naming.Context
import javax.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.RequestParam

class Subject {
  fun constant(request: HttpServletRequest, context: Context) {
    val unused = request.getParameter("x")
    context.lookup("fixed")
  }

  fun overwritten(request: HttpServletRequest, context: Context) {
    var value = request.getParameter("x")
    value = "fixed"
    context.lookup(value)
  }
}
