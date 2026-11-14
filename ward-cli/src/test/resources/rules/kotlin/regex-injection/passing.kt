import java.util.regex.Pattern
import javax.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.RequestParam

class Subject {
  fun constant(request: HttpServletRequest) {
    val unused = request.getParameter("x")
    Pattern.compile("fixed")
  }

  fun overwritten(request: HttpServletRequest) {
    var value = request.getParameter("x")
    value = "fixed"
    Pattern.compile(value)
  }
}
