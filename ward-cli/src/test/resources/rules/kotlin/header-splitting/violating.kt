import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import org.springframework.web.bind.annotation.RequestParam

class Subject {
  fun direct(request: HttpServletRequest, response: HttpServletResponse) {
    response.setHeader("X-Origin", request.getParameter("x"))
  }

  fun propagated(request: HttpServletRequest, response: HttpServletResponse) {
    val value = request?.getParameter("x") ?: ""
    val text = "<$value>"
    response.setHeader("X-Origin", text)
  }

  fun annotated(@RequestParam value: String, response: HttpServletResponse) {
    response.setHeader("X-Origin", "[${value.trim()}]")
  }

  fun crossMethod(request: HttpServletRequest, response: HttpServletResponse) {
    val value = read(request)
    response.setHeader("X-Origin", value)
  }

  fun read(request: HttpServletRequest): String? = request.getParameter("x")
}
