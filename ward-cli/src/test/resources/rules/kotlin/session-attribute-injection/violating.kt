import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpSession
import org.springframework.web.bind.annotation.RequestParam

class Subject {
  fun direct(request: HttpServletRequest, session: HttpSession) {
    session.setAttribute("role", request.getParameter("x"))
  }

  fun propagated(request: HttpServletRequest, session: HttpSession) {
    val value = request?.getParameter("x") ?: ""
    val text = "<$value>"
    session.setAttribute("role", text)
  }

  fun annotated(@RequestParam value: String, session: HttpSession) {
    session.setAttribute("role", "[${value.trim()}]")
  }

  fun crossMethod(request: HttpServletRequest, session: HttpSession) {
    val value = read(request)
    session.setAttribute("role", value)
  }

  fun read(request: HttpServletRequest): String? = request.getParameter("x")
}
