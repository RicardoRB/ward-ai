import javax.servlet.http.HttpServletRequest
import org.slf4j.Logger
import org.springframework.web.bind.annotation.RequestParam
import org.slf4j.LoggerFactory

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

class FactoryLogger {
  private val log = LoggerFactory.getLogger(FactoryLogger::class.java)

  fun record(@RequestParam message: String) {
    log.info("event $message")
  }
}

class LoggingCaller(private val audit: AuditTrail) {
  fun handle(@RequestParam message: String) {
    audit.note(message)
  }
}

class AuditTrail {
  private val log = LoggerFactory.getLogger(AuditTrail::class.java)

  fun note(text: String) {
    log.info("event " + text)
  }
}
