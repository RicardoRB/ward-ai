import javax.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.RequestParam

class Subject {
  fun direct(request: HttpServletRequest, builder: ProcessBuilder) {
    builder.environment().put("LANG", request.getParameter("x"))
  }

  fun propagated(request: HttpServletRequest, builder: ProcessBuilder) {
    val value = request?.getParameter("x") ?: ""
    val text = "<$value>"
    builder.environment().put("LANG", text)
  }

  fun annotated(@RequestParam value: String, builder: ProcessBuilder) {
    builder.environment().put("LANG", "[${value.trim()}]")
  }

  fun crossMethod(request: HttpServletRequest, builder: ProcessBuilder) {
    val value = read(request)
    builder.environment().put("LANG", value)
  }

  fun read(request: HttpServletRequest): String? = request.getParameter("x")
}
