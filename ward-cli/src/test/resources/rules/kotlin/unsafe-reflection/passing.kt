import javax.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.RequestParam

class Subject {
  fun constant(request: HttpServletRequest) {
    val unused = request.getParameter("x")
    Class.forName("fixed")
  }

  fun overwritten(request: HttpServletRequest) {
    var value = request.getParameter("x")
    value = "fixed"
    Class.forName(value)
  }
}
