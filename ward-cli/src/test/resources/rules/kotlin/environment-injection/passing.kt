import javax.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.RequestParam

class Subject {
  fun constant(request: HttpServletRequest, builder: ProcessBuilder) {
    val unused = request.getParameter("x")
    builder.environment().put("LANG", "fixed")
  }

  fun overwritten(request: HttpServletRequest, builder: ProcessBuilder) {
    var value = request.getParameter("x")
    value = "fixed"
    builder.environment().put("LANG", value)
  }
}
