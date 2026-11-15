import javax.servlet.http.HttpServletRequest
import org.slf4j.Logger
import org.springframework.web.bind.annotation.RequestParam

class Subject {
  fun direct(request: HttpServletRequest, log: Logger) {
    log.info(request.getParameter("x"))
  }

  fun propagated(request: HttpServletRequest, log: Logger) {
    val value = request?.getParameter("x") ?: ""
    val text = "<$value>"
    log.info(text)
  }

  fun annotated(@RequestParam value: String, log: Logger) {
    log.info("[${value.trim()}]")
  }

  fun crossMethod(request: HttpServletRequest, log: Logger) {
    val value = read(request)
    log.info(value)
  }

  fun read(request: HttpServletRequest): String? = request.getParameter("x")
}
