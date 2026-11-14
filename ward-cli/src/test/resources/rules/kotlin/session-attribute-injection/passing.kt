import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpSession
import org.springframework.web.bind.annotation.RequestParam

class Subject {
  fun constant(request: HttpServletRequest, session: HttpSession) {
    val unused = request.getParameter("x")
    session.setAttribute("role", "fixed")
  }

  fun overwritten(request: HttpServletRequest, session: HttpSession) {
    var value = request.getParameter("x")
    value = "fixed"
    session.setAttribute("role", value)
  }
}
