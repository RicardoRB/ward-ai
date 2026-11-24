import javax.servlet.http.HttpServletRequest
import org.slf4j.Logger
import org.springframework.web.bind.annotation.RequestParam
import org.slf4j.LoggerFactory

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

class FactoryLoggerSafe {
  private val log = LoggerFactory.getLogger(FactoryLoggerSafe::class.java)

  fun record(@RequestParam message: String) {
    log.info("event {}", 1)
    log.info("fixed")
  }
}

class LoggingCallerSafe(private val audit: AuditTrailSafe) {
  fun handle(@RequestParam message: String) {
    audit.note(message)
  }
}

class AuditTrailSafe {
  private val log = LoggerFactory.getLogger(AuditTrailSafe::class.java)

  fun note(text: String) {
    log.info("event recorded")
  }
}
