import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import org.springframework.web.bind.annotation.RequestParam

class Subject {
  fun constant(request: HttpServletRequest, response: HttpServletResponse) {
    val unused = request.getParameter("x")
    response.setHeader("X-Origin", "fixed")
  }

  fun overwritten(request: HttpServletRequest, response: HttpServletResponse) {
    var value = request.getParameter("x")
    value = "fixed"
    response.setHeader("X-Origin", value)
  }
}
