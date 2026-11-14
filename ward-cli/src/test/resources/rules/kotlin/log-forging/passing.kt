import javax.servlet.http.HttpServletRequest
import org.slf4j.Logger
import org.springframework.web.bind.annotation.RequestParam

class Subject {
  fun constant(request: HttpServletRequest, log: Logger) {
    val unused = request.getParameter("x")
    log.info("fixed")
  }

  fun overwritten(request: HttpServletRequest, log: Logger) {
    var value = request.getParameter("x")
    value = "fixed"
    log.info(value)
  }
}
